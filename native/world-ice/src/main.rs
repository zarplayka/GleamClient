#![cfg_attr(windows, windows_subsystem = "windows")]

use bytes::BytesMut;
use serde::Deserialize;
use std::{
    error::Error,
    io::{self, BufRead, Write},
    sync::Arc,
    time::Duration,
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpStream,
    sync::mpsc,
    time::timeout,
};
use webrtc::{
    data_channel::{DataChannel, DataChannelEvent},
    peer_connection::{
        PeerConnection, PeerConnectionBuilder, PeerConnectionEventHandler, RTCConfigurationBuilder,
        RTCIceGatheringState, RTCIceServer, RTCSessionDescription,
    },
};

const FRAME: usize = 8 * 1024;
const MAX_SIGNAL: usize = 3000;

#[derive(Deserialize)]
struct Start {
    port: u16,
    secret: String,
    offer: Option<String>,
}

struct Events {
    gathered: mpsc::Sender<()>,
    channel: mpsc::Sender<Arc<dyn DataChannel>>,
}

#[async_trait::async_trait]
impl PeerConnectionEventHandler for Events {
    async fn on_ice_gathering_state_change(&self, state: RTCIceGatheringState) {
        if state == RTCIceGatheringState::Complete {
            let _ = self.gathered.try_send(());
        }
    }

    async fn on_data_channel(&self, channel: Arc<dyn DataChannel>) {
        let _ = self.channel.try_send(channel);
    }
}

fn description(sdp: String, offer: bool) -> Result<RTCSessionDescription, Box<dyn Error>> {
    if sdp.len() > MAX_SIGNAL
        || sdp
            .lines()
            .any(|line| line.starts_with("a=candidate:") && line.contains(" typ relay"))
    {
        return Err("Invalid direct ICE description".into());
    }
    Ok(if offer {
        RTCSessionDescription::offer(sdp)?
    } else {
        RTCSessionDescription::answer(sdp)?
    })
}

async fn run(start: Start, mut input: mpsc::Receiver<String>) -> Result<(), Box<dyn Error>> {
    if start.port < 1024
        || start.secret.len() != 32
        || !start.secret.bytes().all(|b| b.is_ascii_hexdigit())
    {
        return Err("Invalid local bridge".into());
    }
    let (gathered, mut gathering) = mpsc::channel(1);
    let (channel, mut remote_channel) = mpsc::channel(1);
    let runtime = webrtc::runtime::default_runtime().ok_or("WebRTC runtime unavailable")?;
    let pc = PeerConnectionBuilder::new()
        .with_runtime(runtime)
        .with_udp_addrs(vec!["0.0.0.0:0", "[::]:0"])
        .with_configuration(
            RTCConfigurationBuilder::new()
                .with_ice_servers(vec![RTCIceServer {
                    urls: vec![
                        "stun:stun.l.google.com:19302".into(),
                        "stun:stun.cloudflare.com:3478".into(),
                    ],
                    ..Default::default()
                }])
                .build(),
        )
        .with_data_channel_send_buffer_limit(256 * 1024)
        .with_handler(Arc::new(Events { gathered, channel }))
        .build()
        .await?;

    let result = session(&pc, &start, &mut input, &mut gathering, &mut remote_channel).await;
    let _ = pc.close().await;
    result
}

async fn session(
    pc: &impl PeerConnection,
    start: &Start,
    input: &mut mpsc::Receiver<String>,
    gathering: &mut mpsc::Receiver<()>,
    remote_channel: &mut mpsc::Receiver<Arc<dyn DataChannel>>,
) -> Result<(), Box<dyn Error>> {
    let guest_channel;
    let local = if let Some(offer) = &start.offer {
        guest_channel = None;
        pc.set_remote_description(description(offer.clone(), true)?)
            .await?;
        pc.create_answer(None).await?
    } else {
        guest_channel = Some(pc.create_data_channel("gleam-world", None).await?);
        pc.create_offer(None).await?
    };
    pc.set_local_description(local).await?;
    let _ = timeout(Duration::from_millis(2200), gathering.recv()).await;
    let local = pc
        .local_description()
        .await
        .ok_or("No local ICE description")?;
    if local.sdp.len() > MAX_SIGNAL {
        return Err("ICE description exceeds signaling limit".into());
    }
    {
        let mut out = io::stdout().lock();
        serde_json::to_writer(&mut out, &local.sdp)?;
        writeln!(out)?;
        out.flush()?;
    }

    let channel = if let Some(channel) = guest_channel {
        let answer = timeout(Duration::from_secs(16), input.recv())
            .await?
            .ok_or("ICE answer missing")?;
        pc.set_remote_description(description(serde_json::from_str(&answer)?, false)?)
            .await?;
        channel
    } else {
        timeout(Duration::from_secs(16), remote_channel.recv())
            .await?
            .ok_or("ICE channel missing")?
    };
    if channel.label().await? != "gleam-world" {
        return Err("Unexpected data channel".into());
    }
    timeout(Duration::from_secs(16), async {
        loop {
            match channel.poll().await {
                Some(DataChannelEvent::OnOpen) => return Ok(()),
                Some(DataChannelEvent::OnClose) | None => return Err("Direct channel closed"),
                _ => {}
            }
        }
    })
    .await??;

    let mut socket = TcpStream::connect((std::net::Ipv4Addr::LOCALHOST, start.port)).await?;
    socket.set_nodelay(true)?;
    socket.write_all(start.secret.as_bytes()).await?;
    let (mut reader, mut writer) = socket.into_split();
    let send = async {
        let mut buffer = [0; FRAME + 1];
        loop {
            let n = reader.read(&mut buffer[1..]).await?;
            if n == 0 {
                channel.send(BytesMut::from(&[1][..])).await?;
                return Ok::<(), Box<dyn Error>>(());
            }
            channel.send(BytesMut::from(&buffer[..n + 1])).await?;
        }
    };
    let receive = async {
        loop {
            match channel.poll().await {
                Some(DataChannelEvent::OnMessage(message)) => match message.data.split_first() {
                    Some((0, data)) if data.len() <= FRAME => writer.write_all(data).await?,
                    Some((1, [])) => {
                        writer.shutdown().await?;
                        return Ok(());
                    }
                    _ => return Err("Invalid game frame".into()),
                },
                Some(DataChannelEvent::OnClose) | None => return Ok::<(), Box<dyn Error>>(()),
                _ => {}
            }
        }
    };
    tokio::try_join!(send, receive)?;
    Ok(())
}

#[tokio::main(worker_threads = 2)]
async fn main() -> Result<(), Box<dyn Error>> {
    let (send, mut input) = mpsc::channel(2);
    std::thread::spawn(move || {
        for line in io::stdin().lock().lines() {
            match line {
                Ok(line) if line.len() <= 8192 => {
                    if send.blocking_send(line).is_err() {
                        break;
                    }
                }
                _ => break,
            }
        }
        // The owner keeps stdin open for the entire session.
        std::process::exit(0);
    });
    let start = input.recv().await.ok_or("Missing start message")?;
    run(serde_json::from_str(&start)?, input).await
}
