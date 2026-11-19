use serde::Deserialize;
use serde_json::{json, Value};
use std::{
    io::{self, Read, Write},
    path::PathBuf,
    process::{self, Command},
    sync::{Arc, Mutex},
    thread,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};

const APPLICATION: &str = "1520703983163932832";
const SITE: &str = "https://gleamclient.xyz/";
const ART: &str = "https://gleamclient.xyz/assets/orion-badge.png";
const UPDATE_INTERVAL: Duration = Duration::from_millis(4200);

#[derive(Clone)]
pub struct Presence(Arc<Mutex<State>>);

struct State {
    enabled: bool,
    selection: String,
    website: Option<Instant>,
    game: Option<Running>,
    started: u64,
    stopping: bool,
}

struct Running {
    version: String,
    file: Option<PathBuf>,
    started: Instant,
    pid: u32,
}

pub struct GamePresence {
    presence: Presence,
    _relay: Option<tempfile::TempDir>,
}

#[derive(Deserialize)]
struct GameStatus {
    enabled: bool,
    mode: String,
}

#[derive(PartialEq, Eq)]
struct Activity {
    pid: u32,
    details: &'static str,
    state: String,
    started: u64,
}

impl Default for Presence {
    fn default() -> Self {
        Self(Arc::new(Mutex::new(State {
            enabled: false,
            selection: String::new(),
            website: None,
            game: None,
            started: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs(),
            stopping: false,
        })))
    }
}

impl Presence {
    pub fn start(&self) {
        let presence = self.clone();
        let _ = thread::Builder::new()
            .name("Gleam Discord Presence".into())
            .spawn(move || presence.run());
    }

    pub fn stop(&self) {
        if let Ok(mut state) = self.0.lock() {
            state.stopping = true;
        }
    }

    pub fn site_opened(&self) {
        if let Ok(mut state) = self.0.lock() {
            state.website = Some(Instant::now() + Duration::from_secs(75));
        }
    }

    pub fn focused(&self) {
        if let Ok(mut state) = self.0.lock() {
            state.website = None;
        }
    }

    pub fn game(&self, command: &mut Command, version: &str) -> GamePresence {
        let relay = tempfile::Builder::new()
            .prefix("gleam-discord-")
            .tempdir()
            .ok();
        let file = relay.as_ref().map(|dir| dir.path().join("state.json"));
        command.env_remove("GLEAM_DISCORD_STATE");
        command.env_remove("GLEAM_DISCORD_OWNER");
        command.env_remove("GLEAM_DISCORD_STARTED");
        if let Some(file) = &file {
            command.env("GLEAM_DISCORD_STATE", file);
            command.env("GLEAM_DISCORD_OWNER", process::id().to_string());
        }
        if let Ok(mut state) = self.0.lock() {
            command.env("GLEAM_DISCORD_STARTED", state.started.to_string());
            state.website = None;
            state.game = Some(Running {
                version: version.to_owned(),
                file,
                started: Instant::now(),
                pid: process::id(),
            });
        }
        GamePresence {
            presence: self.clone(),
            _relay: relay,
        }
    }

    fn run(&self) {
        let mut connection = None;
        let mut published: Option<Activity> = None;
        let mut last_update = None;
        let mut retry = Instant::now();
        loop {
            let (activity, stopping) = {
                let Ok(state) = self.0.lock() else { break };
                (state.activity(), state.stopping)
            };
            if stopping {
                if let Some(mut ipc) = connection {
                    if let Some(activity) = &published {
                        let _ = set_activity(&mut ipc, activity.pid, None);
                    }
                }
                break;
            }
            let now = Instant::now();
            if connection.is_none() && activity.is_some() && now >= retry {
                connection = connect().ok();
                published = None;
                last_update = None;
                retry = now + Duration::from_secs(8);
            }
            if let Some(ipc) = &mut connection {
                let handoff =
                    matches!((&published, &activity), (Some(old), Some(new)) if old.pid != new.pid);
                let ready = handoff
                    || last_update.is_none_or(|last| now.duration_since(last) >= UPDATE_INTERVAL);
                let refresh = last_update
                    .is_some_and(|last| now.duration_since(last) >= Duration::from_secs(40));
                if ready && (activity != published || refresh && activity.is_some()) {
                    let pid = activity
                        .as_ref()
                        .or(published.as_ref())
                        .map_or(process::id(), |a| a.pid);
                    if let Some(previous) = &published {
                        if previous.pid != pid && previous.pid == process::id() {
                            let _ = set_activity(ipc, previous.pid, None);
                        }
                    }
                    match set_activity(ipc, pid, activity.as_ref()) {
                        Ok(()) => {
                            published = activity;
                            last_update = Some(now);
                        }
                        Err(_) => {
                            connection = None;
                            retry = now + Duration::from_secs(8);
                        }
                    }
                }
            }
            thread::sleep(Duration::from_millis(250));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn selection_site_and_game_share_the_same_timer() {
        let presence = Presence::default();
        {
            let mut state = presence.0.lock().unwrap();
            state.enabled = true;
            state.selection = "26.3".into();
        }
        let read = || presence.0.lock().unwrap().activity().unwrap();
        let started = read().started;
        assert_eq!(read().details, "Выбирает сборку");
        presence.site_opened();
        assert_eq!(read().state, "gleamclient.xyz");
        presence.focused();
        let mut command = Command::new("unused");
        let game = presence.game(&mut command, "26.3");
        assert_eq!(read().details, "Запускает Minecraft");
        game.set_process(1000);
        assert_eq!(read().pid, 1000);
        let relay = presence
            .0
            .lock()
            .unwrap()
            .game
            .as_ref()
            .unwrap()
            .file
            .clone()
            .unwrap();
        std::fs::write(&relay, r#"{"enabled":true,"mode":"multiplayer"}"#).unwrap();
        assert_eq!(read().details, "Сетевая игра");
        assert_eq!(read().started, started);
        std::fs::write(&relay, r#"{"enabled":false,"mode":"menu"}"#).unwrap();
        assert!(presence.0.lock().unwrap().activity().is_none());
        drop(game);
        assert_eq!(read().details, "Выбирает сборку");
        assert_eq!(read().pid, process::id());
        assert!(!relay.exists());
    }
}

impl GamePresence {
    pub fn set_process(&self, pid: u32) {
        if let Ok(mut state) = self.presence.0.lock() {
            if let Some(game) = state.game.as_mut() {
                game.pid = pid;
            }
        }
    }
}

impl Drop for GamePresence {
    fn drop(&mut self) {
        if let Ok(mut state) = self.presence.0.lock() {
            state.game = None;
        }
    }
}

impl State {
    fn activity(&self) -> Option<Activity> {
        if let Some(game) = &self.game {
            let report = game.file.as_ref().and_then(|path| {
                let file = std::fs::File::open(path).ok()?;
                if !file.metadata().ok()?.is_file() {
                    return None;
                }
                let mut bytes = Vec::with_capacity(128);
                file.take(1025).read_to_end(&mut bytes).ok()?;
                if bytes.len() > 1024 {
                    return None;
                }
                serde_json::from_slice::<GameStatus>(&bytes).ok()
            });
            let (enabled, details) = match report.as_ref() {
                Some(report) => (
                    report.enabled,
                    match report.mode.as_str() {
                        "singleplayer" => "Одиночная игра",
                        "multiplayer" => "Сетевая игра",
                        _ => "Главное меню Minecraft",
                    },
                ),
                None if game.started.elapsed() < Duration::from_secs(30) => {
                    (self.enabled, "Запускает Minecraft")
                }
                None => (self.enabled, "В игре"),
            };
            return enabled.then(|| Activity {
                pid: game.pid,
                details,
                state: format!("Minecraft {}", game.version),
                started: self.started,
            });
        }
        if !self.enabled {
            return None;
        }
        let website = self.website.is_some_and(|until| until > Instant::now());
        Some(Activity {
            pid: process::id(),
            details: if website {
                "Открыл сайт GleamClient"
            } else {
                "Выбирает сборку"
            },
            state: if website {
                "gleamclient.xyz".into()
            } else if self.selection.is_empty() {
                "Gleam Launcher".into()
            } else {
                format!("Minecraft {}", self.selection)
            },
            started: self.started,
        })
    }
}

#[tauri::command]
pub fn discord_presence_selection(
    presence: tauri::State<'_, Presence>,
    version: String,
    enabled: bool,
) {
    if version.len() > 32
        || !version
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || b".-_".contains(&c))
    {
        return;
    }
    if let Ok(mut state) = presence.0.lock() {
        state.enabled = enabled;
        state.selection = version;
    }
}

fn set_activity(ipc: &mut Transport, pid: u32, activity: Option<&Activity>) -> io::Result<()> {
    let nonce = uuid::Uuid::new_v4().to_string();
    let activity = activity.map(|activity| {
        json!({
            "type": 0, "details": activity.details, "state": activity.state,
            "timestamps": {"start": activity.started},
            "assets": {"large_image": ART, "large_text": "GleamClient", "large_url": SITE},
            "buttons": [{"label": "Сайт GleamClient", "url": SITE}],
        })
    });
    write_frame(
        ipc,
        1,
        &json!({
            "cmd": "SET_ACTIVITY", "nonce": nonce, "args": {"pid": pid, "activity": activity},
        }),
    )?;
    expect_frame(ipc, |frame| frame["nonce"] == nonce)
}

fn connect() -> io::Result<Transport> {
    for index in 0..10 {
        if let Ok(mut ipc) = Transport::open(index) {
            write_frame(&mut ipc, 0, &json!({"v": 1, "client_id": APPLICATION}))?;
            expect_frame(&mut ipc, |frame| frame["evt"] == "READY")?;
            return Ok(ipc);
        }
    }
    Err(io::Error::new(
        io::ErrorKind::NotFound,
        "Discord IPC unavailable",
    ))
}

fn write_frame(ipc: &mut Transport, opcode: u32, value: &Value) -> io::Result<()> {
    let payload = value.to_string();
    let mut bytes = Vec::with_capacity(payload.len() + 8);
    bytes.extend(opcode.to_le_bytes());
    bytes.extend((payload.len() as u32).to_le_bytes());
    bytes.extend(payload.as_bytes());
    ipc.0.write_all(&bytes)
}

fn expect_frame(ipc: &mut Transport, matches: impl Fn(&Value) -> bool) -> io::Result<()> {
    let end = Instant::now() + Duration::from_secs(2);
    while Instant::now() < end {
        let mut header = [0u8; 8];
        ipc.read(&mut header, end)?;
        let opcode = u32::from_le_bytes(header[..4].try_into().unwrap());
        let size = u32::from_le_bytes(header[4..].try_into().unwrap()) as usize;
        if size > 65_536 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "Discord IPC frame too large",
            ));
        }
        let mut bytes = vec![0; size];
        ipc.read(&mut bytes, end)?;
        let frame: Value = serde_json::from_slice(&bytes).map_err(io::Error::other)?;
        match opcode {
            2 => break,
            3 => write_frame(ipc, 4, &frame)?,
            1 if frame["evt"] == "ERROR" => {
                return Err(io::Error::other("Discord rejected presence"))
            }
            1 if matches(&frame) => return Ok(()),
            _ => {}
        }
    }
    Err(io::Error::new(
        io::ErrorKind::TimedOut,
        "Discord IPC response missing",
    ))
}

#[cfg(windows)]
type Pipe = std::fs::File;
#[cfg(unix)]
type Pipe = std::os::unix::net::UnixStream;
struct Transport(Pipe);

impl Transport {
    #[cfg(windows)]
    fn open(index: u8) -> io::Result<Self> {
        std::fs::OpenOptions::new()
            .read(true)
            .write(true)
            .open(format!(r"\\.\pipe\discord-ipc-{index}"))
            .map(Self)
    }

    #[cfg(unix)]
    fn open(index: u8) -> io::Result<Self> {
        let base = std::env::var_os("XDG_RUNTIME_DIR")
            .map(PathBuf::from)
            .unwrap_or_else(std::env::temp_dir);
        Pipe::connect(base.join(format!("discord-ipc-{index}")))
            .or_else(|_| Pipe::connect(std::env::temp_dir().join(format!("discord-ipc-{index}"))))
            .map(Self)
    }

    #[cfg(windows)]
    fn read(&mut self, buffer: &mut [u8], deadline: Instant) -> io::Result<()> {
        use std::{ffi::c_void, os::windows::io::AsRawHandle, ptr::null_mut};
        #[link(name = "kernel32")]
        extern "system" {
            fn PeekNamedPipe(
                handle: *mut c_void,
                buffer: *mut c_void,
                size: u32,
                read: *mut u32,
                available: *mut u32,
                remainder: *mut u32,
            ) -> i32;
        }
        let mut filled = 0;
        while filled < buffer.len() && Instant::now() < deadline {
            let mut available = 0;
            let ok = unsafe {
                PeekNamedPipe(
                    self.0.as_raw_handle(),
                    null_mut(),
                    0,
                    null_mut(),
                    &mut available,
                    null_mut(),
                )
            };
            if ok == 0 {
                return Err(io::Error::last_os_error());
            }
            if available == 0 {
                thread::sleep(Duration::from_millis(12));
                continue;
            }
            let end = buffer.len().min(filled + available as usize);
            let count = self.0.read(&mut buffer[filled..end])?;
            if count == 0 {
                return Err(io::ErrorKind::UnexpectedEof.into());
            }
            filled += count;
        }
        if filled == buffer.len() {
            Ok(())
        } else {
            Err(io::ErrorKind::TimedOut.into())
        }
    }

    #[cfg(unix)]
    fn read(&mut self, buffer: &mut [u8], deadline: Instant) -> io::Result<()> {
        self.0.set_read_timeout(Some(
            deadline
                .saturating_duration_since(Instant::now())
                .max(Duration::from_millis(1)),
        ))?;
        self.0.read_exact(buffer)
    }
}
