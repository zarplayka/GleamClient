use crate::{catalog, profile_import};
use base64::{engine::general_purpose::STANDARD, Engine};
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use std::{
    fs,
    io::{self, Read, Write},
    net::{IpAddr, TcpStream, ToSocketAddrs},
    path::{Component, Path, PathBuf},
    time::{Duration, Instant},
};

const READ_ERROR: &str = "Не удалось прочитать содержимое сборки";
const SERVER_LIMIT: u64 = 8 * 1024 * 1024;

#[derive(Clone, Copy, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    Mods,
    Shaders,
    Resourcepacks,
    Worlds,
    Servers,
    Logs,
}

impl Kind {
    fn folder(self) -> Option<&'static str> {
        Some(match self {
            Self::Mods => "mods",
            Self::Shaders => "shaderpacks",
            Self::Resourcepacks => "resourcepacks",
            Self::Worlds => "saves",
            Self::Logs => "logs",
            Self::Servers => return None,
        })
    }
}

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    name: String,
    file_name: String,
    description: String,
    version: String,
    bytes: u64,
    enabled: bool,
    managed: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    icon: Option<String>,
}

pub(crate) fn metadata(path: &Path) -> io::Result<Option<fs::Metadata>> {
    match fs::symlink_metadata(path) {
        Ok(value) => {
            if value.is_symlink() {
                return Ok(None);
            }
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                if value.file_attributes() & 0x400 != 0 {
                    return Ok(None);
                }
            }
            Ok(Some(value))
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error),
    }
}

fn instance(
    app: &tauri::AppHandle,
    id: Option<&str>,
    version: Option<&str>,
) -> Result<PathBuf, String> {
    let Some(id) = id else {
        let version = version.ok_or("Профиль не найден")?;
        catalog::validate_version(version)?;
        let path = catalog::instance_dir(app, version)?;
        return match fs::symlink_metadata(&path) {
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(path),
            Ok(_)
                if metadata(&path).is_ok_and(|value| value.is_some_and(|value| value.is_dir())) =>
            {
                Ok(path)
            }
            _ => Err(READ_ERROR.into()),
        };
    };
    profile_import::installed(app)?
        .into_iter()
        .find_map(|(profile, path)| (profile.id == id).then_some(path))
        .filter(|path| metadata(path).is_ok_and(|value| value.is_some_and(|value| value.is_dir())))
        .ok_or_else(|| "Профиль не найден".into())
}

fn single_component(name: &str) -> bool {
    !name.is_empty()
        && !name
            .chars()
            .any(|ch| ch.is_control() || "\\/:*?\"<>|".contains(ch))
        && matches!(
            Path::new(name).components().collect::<Vec<_>>().as_slice(),
            [Component::Normal(_)]
        )
}

fn mod_name(name: &str) -> Option<(&str, bool)> {
    let enabled_name = name.strip_suffix(".disabled").unwrap_or(name);
    Path::new(enabled_name)
        .extension()
        .is_some_and(|value| value.eq_ignore_ascii_case("jar"))
        .then_some((enabled_name, name == enabled_name))
}

fn managed_mod(name: &str, id: &str) -> bool {
    name.eq_ignore_ascii_case("gleamclient.jar")
        || name.eq_ignore_ascii_case("gleam-fabric-api.jar")
        || matches!(id, "fabric-api" | "orion-client")
}

fn mod_icon(path: &Path, value: &serde_json::Value) -> Option<String> {
    let name = value.as_str().or_else(|| {
        value
            .as_object()?
            .iter()
            .filter_map(|(size, path)| Some((size.parse::<u32>().ok()?, path.as_str()?)))
            .min_by_key(|(size, _)| size.abs_diff(64))
            .map(|(_, path)| path)
    })?;
    if !name.to_ascii_lowercase().ends_with(".png") {
        return None;
    }
    const MAX_ICON: u64 = 48 * 1024;
    let mut jar = zip::ZipArchive::new(fs::File::open(path).ok()?).ok()?;
    let file = jar.by_name(name).ok()?;
    if file.size() > MAX_ICON {
        return None;
    }
    let mut bytes = Vec::new();
    file.take(MAX_ICON + 1).read_to_end(&mut bytes).ok()?;
    if bytes.len() as u64 > MAX_ICON
        || !bytes.starts_with(b"\x89PNG\r\n\x1a\n")
        || bytes.get(12..16)? != b"IHDR"
    {
        return None;
    }
    let width = u32::from_be_bytes(bytes.get(16..20)?.try_into().ok()?);
    let height = u32::from_be_bytes(bytes.get(20..24)?.try_into().ok()?);
    if !(1..=256).contains(&width) || !(1..=256).contains(&height) {
        return None;
    }
    Some(format!("data:image/png;base64,{}", STANDARD.encode(bytes)))
}

fn folder_entries(instance: &Path, kind: Kind) -> io::Result<Vec<Entry>> {
    let folder = instance.join(kind.folder().expect("directory kind"));
    if !metadata(&folder)?.is_some_and(|value| value.is_dir()) {
        return Ok(Vec::new());
    }
    let mut entries = Vec::new();
    for item in fs::read_dir(folder)? {
        let item = item?;
        let Ok(file_name) = item.file_name().into_string() else {
            continue;
        };
        let path = item.path();
        let Some(info) = metadata(&path)? else {
            continue;
        };
        let lower = file_name.to_ascii_lowercase();
        let included = match kind {
            Kind::Mods => info.is_file() && mod_name(&file_name).is_some(),
            Kind::Shaders => {
                info.is_file() && lower.ends_with(".zip")
                    || info.is_dir() && path.join("shaders").is_dir()
            }
            Kind::Resourcepacks => {
                info.is_file() && lower.ends_with(".zip")
                    || info.is_dir() && path.join("pack.mcmeta").is_file()
            }
            Kind::Worlds => info.is_dir() && path.join("level.dat").is_file(),
            Kind::Logs => info.is_file() && (lower.ends_with(".log") || lower.ends_with(".log.gz")),
            Kind::Servers => false,
        };
        if !included {
            continue;
        }
        let mut entry = Entry {
            name: file_name.clone(),
            file_name,
            bytes: if info.is_file() { info.len() } else { 0 },
            ..Entry::default()
        };
        if let Kind::Mods = kind {
            let (enabled_name, enabled) = mod_name(&entry.file_name).expect("mod filename");
            let json = catalog::fabric_mod_metadata(&path).unwrap_or_default();
            entry.icon = mod_icon(&path, &json["icon"]);
            entry.enabled = enabled;
            entry.managed = managed_mod(enabled_name, json["id"].as_str().unwrap_or_default());
            entry.name = json["name"]
                .as_str()
                .filter(|name| !name.is_empty())
                .unwrap_or(enabled_name)
                .chars()
                .take(160)
                .collect();
            entry.description = json["description"]
                .as_str()
                .unwrap_or_default()
                .chars()
                .take(420)
                .collect();
            entry.version = json["version"]
                .as_str()
                .unwrap_or_default()
                .chars()
                .take(90)
                .collect();
        }
        entries.push(entry);
    }
    entries.sort_by_cached_key(|entry| {
        (
            kind == Kind::Logs && entry.file_name != "latest.log",
            entry.name.to_lowercase(),
        )
    });
    Ok(entries)
}

#[tauri::command]
pub async fn open_crash_report(app: tauri::AppHandle, profile_id: Option<String>, game_version: Option<String>, file: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        if !crate::game::valid_crash_report(&file) { return Err("Не удалось открыть отчёт".into()); }
        let folder = instance(&app, profile_id.as_deref(), game_version.as_deref())?.join("crash-reports");
        let path = folder.join(file);
        if !metadata(&folder).is_ok_and(|value| value.is_some_and(|value| value.is_dir()))
            || !metadata(&path).is_ok_and(|value| value.is_some_and(|value| value.is_file())) {
            return Err("Не удалось открыть отчёт".into());
        }
        catalog::open_local_path(&path)
    }).await.map_err(|_| "Не удалось открыть отчёт".to_string())?
}

fn take<'a>(input: &mut &'a [u8], count: usize) -> Option<&'a [u8]> {
    let (bytes, rest) = input.split_at_checked(count)?;
    *input = rest;
    Some(bytes)
}

fn byte(input: &mut &[u8]) -> Option<u8> {
    Some(take(input, 1)?[0])
}

fn count(input: &mut &[u8]) -> Option<usize> {
    i32::from_be_bytes(take(input, 4)?.try_into().ok()?)
        .try_into()
        .ok()
}

fn string<'a>(input: &mut &'a [u8]) -> Option<&'a [u8]> {
    let size = u16::from_be_bytes(take(input, 2)?.try_into().ok()?);
    take(input, usize::from(size))
}

fn skip(input: &mut &[u8], tag: u8, depth: u8) -> Option<()> {
    if depth > 24 {
        return None;
    }
    match tag {
        1 => {
            take(input, 1)?;
        }
        2 => {
            take(input, 2)?;
        }
        3 | 5 => {
            take(input, 4)?;
        }
        4 | 6 => {
            take(input, 8)?;
        }
        7 | 11 | 12 => {
            let width = if tag == 7 {
                1
            } else if tag == 11 {
                4
            } else {
                8
            };
            let size = count(input)?.checked_mul(width)?;
            take(input, size)?;
        }
        8 => {
            string(input)?;
        }
        9 => {
            let kind = byte(input)?;
            let size = count(input)?;
            if size > input.len() || size > 65_536 {
                return None;
            }
            for _ in 0..size {
                skip(input, kind, depth + 1)?;
            }
        }
        10 => loop {
            let kind = byte(input)?;
            if kind == 0 {
                break;
            }
            string(input)?;
            skip(input, kind, depth + 1)?;
        },
        _ => return None,
    }
    Some(())
}

fn parse_servers(mut input: &[u8]) -> Option<Vec<Entry>> {
    if byte(&mut input)? != 10 {
        return None;
    }
    string(&mut input)?;
    loop {
        let tag = byte(&mut input)?;
        if tag == 0 {
            return Some(Vec::new());
        }
        if string(&mut input)? == b"servers" && tag == 9 {
            break;
        }
        skip(&mut input, tag, 0)?;
    }
    let kind = byte(&mut input)?;
    let size = count(&mut input)?;
    if size == 0 {
        return Some(Vec::new());
    }
    if kind != 10 || size > input.len() {
        return None;
    }
    let mut entries = Vec::new();
    for _ in 0..size {
        let mut entry = Entry::default();
        loop {
            let tag = byte(&mut input)?;
            if tag == 0 {
                break;
            }
            match (tag, string(&mut input)?) {
                (8, b"name") => {
                    entry.name = String::from_utf8_lossy(string(&mut input)?)
                        .chars()
                        .take(160)
                        .collect()
                }
                (8, b"ip") => {
                    entry.description = String::from_utf8_lossy(string(&mut input)?)
                        .chars()
                        .take(256)
                        .collect()
                }
                _ => skip(&mut input, tag, 0)?,
            }
        }
        if !entry.description.is_empty() {
            if entry.name.is_empty() {
                entry.name.clone_from(&entry.description);
            }
            entries.push(entry);
        }
    }
    Some(entries)
}

fn server_entries(instance: &Path) -> io::Result<Vec<Entry>> {
    let path = instance.join("servers.dat");
    if !metadata(&path)?.is_some_and(|value| value.is_file()) {
        return Ok(Vec::new());
    }
    let mut input = Vec::new();
    fs::File::open(path)?
        .take(SERVER_LIMIT + 1)
        .read_to_end(&mut input)?;
    if input.starts_with(&[0x1f, 0x8b]) {
        let mut decoded = Vec::new();
        GzDecoder::new(input.as_slice())
            .take(SERVER_LIMIT + 1)
            .read_to_end(&mut decoded)?;
        input = decoded;
    }
    (input.len() as u64 <= SERVER_LIMIT)
        .then(|| parse_servers(&input))
        .flatten()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, READ_ERROR))
}

pub(crate) fn server_address(address: &str) -> Option<(&str, u16)> {
    fn port(value: &str) -> Option<u16> {
        value.parse::<u16>().ok().filter(|&port| port != 0)
    }
    if address.is_empty() || address.len() > 255 || address.trim() != address {
        return None;
    }
    if let Some(address) = address.strip_prefix('[') {
        let (host, suffix) = address.split_once(']')?;
        host.parse::<std::net::Ipv6Addr>().ok()?;
        return Some((host, if suffix.is_empty() { 25565 } else { port(suffix.strip_prefix(':')?)? }));
    }
    if address.parse::<IpAddr>().is_ok() {
        return Some((address, 25565));
    }
    let (host, port) = match address.split_once(':') {
        Some((host, suffix)) => (host, port(suffix)?),
        None => (address, 25565),
    };
    let valid = host.trim_end_matches('.').split('.').all(|label| {
        !label.is_empty() && label.len() <= 63
            && label.bytes().all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
    });
    valid.then_some((host, port))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    ping_ms: u64,
    motd: String,
    version: String,
    online: Option<u64>,
    max: Option<u64>,
    icon: Option<String>,
}

fn varint(output: &mut Vec<u8>, mut value: u32) {
    while value >= 128 {
        output.push(value as u8 | 0x80);
        value >>= 7;
    }
    output.push(value as u8);
}

fn read_varint(input: &mut impl Read) -> io::Result<u32> {
    let mut value = 0;
    for shift in [0, 7, 14, 21, 28] {
        let mut byte = [0];
        input.read_exact(&mut byte)?;
        if shift == 28 && byte[0] > 15 {
            break;
        }
        value |= u32::from(byte[0] & 0x7f) << shift;
        if byte[0] & 0x80 == 0 {
            return Ok(value);
        }
    }
    Err(io::ErrorKind::InvalidData.into())
}

fn plain_server_text(value: &serde_json::Value, output: &mut String, depth: u8) {
    if depth > 12 || output.len() > 2048 {
        return;
    }
    if let Some(text) = value.as_str() {
        let mut chars = text.chars();
        while let Some(ch) = chars.next() {
            if ch == '§' {
                chars.next();
            } else if !ch.is_control() || ch == '\n' {
                output.push(ch);
                if output.len() > 2048 { break; }
            }
        }
    } else if let Some(parts) = value.as_array() {
        for part in parts {
            plain_server_text(part, output, depth + 1);
        }
    } else if value.is_object() {
        plain_server_text(&value["text"], output, depth + 1);
        plain_server_text(&value["extra"], output, depth + 1);
    }
}

struct StatusReader<'a>(&'a mut TcpStream, Instant);

impl Read for StatusReader<'_> {
    fn read(&mut self, bytes: &mut [u8]) -> io::Result<usize> {
        let remaining = self.1.saturating_duration_since(Instant::now());
        if remaining.is_zero() {
            return Err(io::ErrorKind::TimedOut.into());
        }
        self.0.set_read_timeout(Some(remaining))?;
        self.0.read(bytes)
    }
}

#[cfg(windows)]
fn server_srv(host: &str) -> Option<(String, u16)> {
    use std::ffi::c_void;
    use std::ptr;

    const SRV: u16 = 33;
    #[repr(C)]
    struct Record {
        next: *mut Record,
        name: *mut u16,
        kind: u16,
        data_length: u16,
        flags: u32,
        ttl: u32,
        reserved: u32,
    }
    #[repr(C)]
    struct SrvRecord {
        header: Record,
        target: *const u16,
        priority: u16,
        weight: u16,
        port: u16,
        pad: u16,
    }
    #[link(name = "dnsapi")]
    extern "system" {
        fn DnsQuery_W(name: *const u16, kind: u16, flags: u32, extra: *mut c_void,
                      records: *mut *mut Record, reserved: *mut *mut c_void) -> u32;
        fn DnsFree(records: *mut c_void, free_type: i32);
    }

    let query: Vec<_> = format!("_minecraft._tcp.{host}").encode_utf16().chain([0]).collect();
    let mut records = ptr::null_mut();
    let mut best = None;
    // DNSAPI owns the linked list and the NUL-terminated wide strings until DnsFree.
    unsafe {
        let status = DnsQuery_W(query.as_ptr(), SRV, 0, ptr::null_mut(), &mut records, ptr::null_mut());
        let mut current = records;
        while status == 0 && !current.is_null() {
            let record = &*current;
            let srv = current.cast::<SrvRecord>();
            current = record.next;
            if record.kind != SRV || usize::from(record.data_length) < std::mem::size_of::<SrvRecord>() - std::mem::size_of::<Record>() {
                continue;
            }
            let record = &*srv;
            if record.target.is_null() || record.port == 0 {
                continue;
            }
            let order = (record.priority, std::cmp::Reverse(record.weight));
            if best.as_ref().is_none_or(|(previous, _)| order < *previous) {
                let length = (0..=255).find(|&index| *record.target.add(index) == 0);
                if let Some(length) = length {
                    let target = String::from_utf16_lossy(std::slice::from_raw_parts(record.target, length));
                    if server_address(&target).is_some() {
                        best = Some((order, (target, record.port)));
                    }
                }
            }
        }
        if !records.is_null() {
            DnsFree(records.cast(), 1); // DnsFreeRecordList
        }
    }
    best.map(|(_, target)| target)
}

fn server_status(address: &str) -> Option<ServerStatus> {
    let (host, port) = server_address(address)?;
    #[cfg(windows)]
    let srv = (port == 25565 && host.parse::<IpAddr>().is_err()).then(|| server_srv(host)).flatten();
    #[cfg(not(windows))]
    let srv: Option<(String, u16)> = None;
    let endpoint = srv.as_ref().map_or((host, port), |(target, port)| (target.as_str(), *port));
    let started = Instant::now();
    let deadline = started + Duration::from_secs(3);
    let mut connection = endpoint.to_socket_addrs().ok()?.take(6).find_map(|address| {
        let remaining = deadline.checked_duration_since(Instant::now())?;
        TcpStream::connect_timeout(&address, remaining.min(Duration::from_millis(800))).ok()
    })?;
    connection.set_write_timeout(Some(Duration::from_secs(1))).ok()?;
    let mut handshake = vec![0];
    // -1 requests a status response independent of the selected profile's protocol version.
    varint(&mut handshake, u32::MAX);
    varint(&mut handshake, host.len() as u32);
    handshake.extend(host.as_bytes());
    handshake.extend(port.to_be_bytes());
    handshake.push(1);
    let mut request = Vec::new();
    varint(&mut request, handshake.len() as u32);
    request.extend(handshake);
    request.extend([1, 0]);
    connection.write_all(&request).ok()?;
    let mut reader = StatusReader(&mut connection, deadline);
    let size = read_varint(&mut reader).ok()? as usize;
    if !(2..=128 * 1024).contains(&size) { return None; }
    let mut packet = vec![0; size];
    reader.read_exact(&mut packet).ok()?;
    let mut payload = packet.as_slice();
    if read_varint(&mut payload).ok()? != 0 { return None; }
    if read_varint(&mut payload).ok()? as usize != payload.len() { return None; }
    let reply: serde_json::Value = serde_json::from_slice(payload).ok()?;
    if !reply.is_object() { return None; }
    let mut ping = started.elapsed();
    let sent = Instant::now();
    let ping_packet = [9, 1, 0, 0, 0, 0, 0, 0, 0, 1];
    if connection.write_all(&ping_packet).is_ok() {
        let mut pong = [0; 10];
        if StatusReader(&mut connection, deadline.min(sent + Duration::from_millis(700)))
            .read_exact(&mut pong).is_ok() && pong == ping_packet
        {
            ping = sent.elapsed();
        }
    }
    let mut motd = String::new();
    plain_server_text(&reply["description"], &mut motd, 0);
    let icon = reply["favicon"].as_str().filter(|icon| {
        icon.len() <= 98_304 && icon.starts_with("data:image/png;base64,iVBORw0KGgo")
    }).map(str::to_owned);
    Some(ServerStatus {
        ping_ms: (ping.as_millis() as u64).max(1),
        motd: motd.trim().into(),
        version: reply["version"]["name"].as_str().unwrap_or_default().chars().take(160).collect(),
        online: reply["players"]["online"].as_u64(),
        max: reply["players"]["max"].as_u64(),
        icon,
    })
}

#[tauri::command]
pub async fn profile_server_status(address: String) -> Option<ServerStatus> {
    tauri::async_runtime::spawn_blocking(move || server_status(&address)).await.ok().flatten()
}

#[cfg(test)]
#[test]
fn status_and_latency_are_read_from_the_minecraft_server() {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let worker = std::thread::spawn(move || {
        let (mut client, _) = listener.accept().unwrap();
        client.set_read_timeout(Some(Duration::from_secs(2))).unwrap();
        let handshake_size = read_varint(&mut client).unwrap() as usize;
        let mut handshake_and_request = vec![0; handshake_size + 2];
        client.read_exact(&mut handshake_and_request).unwrap();
        assert_eq!(handshake_and_request[handshake_size..], [1, 0]);
        let status = br#"{"version":{"name":"Velocity"},"players":{"online":19,"max":100},"description":{"text":"Welcome ","extra":[{"text":"to Gleam"}]}}"#;
        let mut payload = vec![0];
        varint(&mut payload, status.len() as u32);
        payload.extend(status);
        let mut response = Vec::new();
        varint(&mut response, payload.len() as u32);
        response.extend(payload);
        client.write_all(&response).unwrap();
        let mut ping = [0; 10];
        client.read_exact(&mut ping).unwrap();
        client.write_all(&ping).unwrap();
    });
    let status = server_status(&address).unwrap();
    worker.join().unwrap();
    assert_eq!(status.motd, "Welcome to Gleam");
    assert_eq!(status.version, "Velocity");
    assert_eq!((status.online, status.max), (Some(19), Some(100)));
    assert!(status.ping_ms >= 1);
    assert_eq!(server_address("[::1]:25566"), Some(("::1", 25566)));
    assert!(server_address("https://example.com").is_none());
}

#[tauri::command]
pub async fn profile_content(
    app: tauri::AppHandle,
    profile_id: Option<String>,
    game_version: Option<String>,
    kind: Kind,
) -> Result<Vec<Entry>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = instance(&app, profile_id.as_deref(), game_version.as_deref())?;
        let entries = if kind == Kind::Servers {
            server_entries(&root)
        } else {
            folder_entries(&root, kind)
        };
        entries.map_err(|_| READ_ERROR.to_string())
    })
    .await
    .map_err(|_| READ_ERROR.to_string())?
}

fn change_mod(root: &Path, file: &str, enabled: bool) -> Result<(), String> {
    let (name, was_enabled) = mod_name(file)
        .filter(|_| single_component(file))
        .ok_or("Не удалось изменить мод")?;
    let folder = root.join("mods");
    if !metadata(&folder).is_ok_and(|value| value.is_some_and(|value| value.is_dir())) {
        return Err("Не удалось изменить мод".into());
    }
    let from = folder.join(file);
    if !metadata(&from).is_ok_and(|value| value.is_some_and(|value| value.is_file())) {
        return Err("Файл мода не найден".into());
    }
    let json = catalog::fabric_mod_metadata(&from).unwrap_or_default();
    if managed_mod(name, json["id"].as_str().unwrap_or_default()) {
        return Err("Управляется лаунчером".into());
    }
    if enabled == was_enabled {
        return Ok(());
    }
    let to = folder.join(if enabled {
        name.to_string()
    } else {
        format!("{name}.disabled")
    });
    // Publishing the new name with a hard link refuses collisions atomically on every platform.
    fs::hard_link(&from, &to).map_err(|_| "Не удалось изменить мод")?;
    if fs::remove_file(&from).is_err() {
        let _ = fs::remove_file(to);
        return Err("Не удалось изменить мод".into());
    }
    Ok(())
}

#[tauri::command]
pub async fn profile_toggle_mod(
    app: tauri::AppHandle,
    profile_id: Option<String>,
    game_version: Option<String>,
    file: String,
    enabled: bool,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = instance(&app, profile_id.as_deref(), game_version.as_deref())?;
        if crate::game::running_game_version(&app)?.is_some() {
            return Err("Закрой Minecraft или дождись завершения установки".into());
        }
        let lock = |name| {
            let path = root.join(name);
            if fs::symlink_metadata(&path).is_ok()
                && !metadata(&path).is_ok_and(|value| value.is_some_and(|value| value.is_file()))
            {
                return Err("Не удалось изменить мод");
            }
            let file = fs::OpenOptions::new()
                .read(true)
                .write(true)
                .create(true)
                .truncate(false)
                .open(path)
                .map_err(|_| "Не удалось изменить мод")?;
            file.try_lock()
                .map_err(|_| "Закрой Minecraft или дождись завершения установки")?;
            Ok(file)
        };
        let _game = lock(".gleam-launch.lock")?;
        let _catalog = lock("catalog-install.lock")?;
        change_mod(&root, &file, enabled)
    })
    .await
    .map_err(|_| "Не удалось изменить мод".to_string())?
}

#[tauri::command]
pub async fn open_profile_content(
    app: tauri::AppHandle,
    profile_id: Option<String>,
    game_version: Option<String>,
    kind: Kind,
    file: Option<String>,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = instance(&app, profile_id.as_deref(), game_version.as_deref())?;
        let folder = root.join(kind.folder().ok_or("Не удалось открыть папку")?);
        if file.is_none() && !folder.exists() {
            fs::create_dir_all(&folder).map_err(|_| "Не удалось открыть папку")?;
        }
        if !metadata(&folder).is_ok_and(|value| value.is_some_and(|value| value.is_dir())) {
            return Err("Не удалось открыть папку".into());
        }
        let path = if let Some(file) = file {
            if !matches!(kind, Kind::Worlds | Kind::Logs)
                || !single_component(&file)
                || !folder_entries(&root, kind)
                    .is_ok_and(|entries| entries.iter().any(|entry| entry.file_name == file))
            {
                return Err("Не удалось открыть файл".into());
            }
            folder.join(file)
        } else {
            folder
        };
        catalog::open_local_path(&path)
    })
    .await
    .map_err(|_| "Не удалось открыть папку".to_string())?
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn mods_keep_their_metadata_and_do_not_overwrite() {
        let root = tempfile::tempdir().unwrap();
        let mods = root.path().join("mods");
        fs::create_dir(&mods).unwrap();
        let mut archive = zip::ZipWriter::new(fs::File::create(mods.join("sodium.jar")).unwrap());
        archive
            .start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        archive
            .write_all(br#"{"id":"sodium","name":"Sodium","version":"1.0"}"#)
            .unwrap();
        archive.finish().unwrap();

        change_mod(root.path(), "sodium.jar", false).unwrap();
        let entries = folder_entries(root.path(), Kind::Mods).unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].name, "Sodium");
        assert_eq!(entries[0].version, "1.0");
        assert!(!entries[0].enabled);
        assert_eq!(catalog::disabled_common_mods(root.path())[0].0, "sodium");
        assert!(change_mod(root.path(), "../sodium.jar.disabled", true).is_err());

        fs::write(mods.join("sodium.jar"), b"different file").unwrap();
        assert!(change_mod(root.path(), "sodium.jar.disabled", true).is_err());
        assert_eq!(
            fs::read(mods.join("sodium.jar")).unwrap(),
            b"different file"
        );
        fs::remove_file(mods.join("sodium.jar")).unwrap();
        change_mod(root.path(), "sodium.jar.disabled", true).unwrap();
        assert!(folder_entries(root.path(), Kind::Mods).unwrap()[0].enabled);
    }

    #[test]
    fn reads_ungzipped_and_gzipped_server_records() {
        let root = tempfile::tempdir().unwrap();
        let bytes = b"\x0a\x00\x00\x09\x00\x07servers\x0a\x00\x00\x00\x01\x08\x00\x04name\x00\x05Local\x01\x00\x06hidden\x00\x08\x00\x02ip\x00\x09localhost\x00\x00";
        let path = root.path().join("servers.dat");
        fs::write(&path, bytes).unwrap();
        assert_eq!(
            server_entries(root.path()).unwrap()[0].description,
            "localhost"
        );
        let mut gzip = flate2::write::GzEncoder::new(
            fs::File::create(&path).unwrap(),
            flate2::Compression::fast(),
        );
        gzip.write_all(bytes).unwrap();
        gzip.finish().unwrap();
        assert_eq!(server_entries(root.path()).unwrap()[0].name, "Local");
        assert!(parse_servers(&bytes[..bytes.len() - 4]).is_none());
    }
}
