use crate::{catalog, profile_import};
use base64::{engine::general_purpose::STANDARD, Engine};
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use std::{
    fs,
    io::{self, Read},
    path::{Component, Path, PathBuf},
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

fn metadata(path: &Path) -> io::Result<Option<fs::Metadata>> {
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
