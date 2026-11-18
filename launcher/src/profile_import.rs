use chrono::{DateTime, Local, NaiveDate, NaiveDateTime, NaiveTime, TimeZone};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs, io,
    io::{BufRead, BufReader, Read},
    path::{Path, PathBuf},
};
use tauri::Manager;

use crate::game::PlaySession;

type Result<T> = std::result::Result<T, String>;

const GAME_FILES: &[&str] = &[
    "saves",
    "mods",
    "config",
    "defaultconfigs",
    "resourcepacks",
    "shaderpacks",
    "screenshots",
    "options.txt",
    "optionsof.txt",
    "servers.dat",
];

pub(crate) const MODRINTH_ACTIVITY: &str = "modrinth-activity.json";

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Profile {
    pub id: String,
    pub name: String,
    pub version: String,
    loader: String,
    launcher: String,
    pub total_ms: u64,
    last_played: Option<u64>,
    playable: bool,
}

impl Profile {
    pub(crate) fn playable(&self) -> bool {
        self.playable
    }
}

struct Candidate {
    profile: Profile,
    path: PathBuf,
    sessions: Vec<PlaySession>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Offer {
    #[serde(flatten)]
    profile: Profile,
    path: String,
    imported: bool,
    sessions: usize,
}

#[derive(Serialize)]
pub struct Discovery {
    profiles: Vec<Offer>,
    onboarding: bool,
}

#[derive(Serialize)]
pub struct ImportResult {
    imported: Vec<String>,
    failed: Vec<String>,
}

fn instances(app: &tauri::AppHandle) -> Result<PathBuf> {
    Ok(app
        .path()
        .app_data_dir()
        .map_err(|_| "game_storage")?
        .join("instances"))
}

fn json(path: &Path) -> Value {
    use std::io::Read;
    let mut data = Vec::new();
    if fs::File::open(path)
        .and_then(|file| file.take(16 * 1024 * 1024 + 1).read_to_end(&mut data))
        .is_err()
        || data.len() > 16 * 1024 * 1024
    {
        return Value::Null;
    }
    serde_json::from_slice(data.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&data))
        .unwrap_or_default()
}

fn ini(path: &Path) -> BTreeMap<String, String> {
    fs::read_to_string(path)
        .unwrap_or_default()
        .lines()
        .filter_map(|line| line.split_once('='))
        .map(|(key, value)| (key.trim().to_owned(), value.trim().to_owned()))
        .collect()
}

fn folders(path: &Path) -> impl Iterator<Item = PathBuf> {
    fs::read_dir(path)
        .into_iter()
        .flatten()
        .filter_map(|entry| {
            let entry = entry.ok()?;
            entry.file_type().ok()?.is_dir().then(|| entry.path())
        })
}

fn text<'a>(value: &'a Value, keys: &[&str]) -> &'a str {
    keys.iter()
        .find_map(|key| value[*key].as_str().filter(|s| !s.is_empty()))
        .unwrap_or_default()
}

fn num(value: &Value, key: &str) -> u64 {
    value[key].as_u64().unwrap_or_default()
}

fn clock_ms(value: u64) -> Option<u64> {
    let millis = if value < 10_000_000_000 { value * 1000 } else { value };
    (millis > 0 && millis <= 8_640_000_000_000_000).then_some(millis)
}

fn named(path: &Path) -> &str {
    path.file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("Minecraft")
}

fn version_hint(label: &str) -> (&str, &str) {
    let loader = if label.contains("fabric") || label.contains("Fabric") {
        "fabric"
    } else if label.contains("neoforge") {
        "neoforge"
    } else if label.contains("forge") || label.contains("Forge") {
        "forge"
    } else if label.contains("quilt") {
        "quilt"
    } else {
        "vanilla"
    };
    let version = label
        .split(['-', ' '])
        .find(|part| {
            part.contains('.')
                && part.split('.').all(|piece| {
                    !piece.is_empty() && piece.bytes().all(|byte| byte.is_ascii_digit())
                })
        })
        .unwrap_or_default();
    (version, loader)
}

fn log_version(path: &Path) -> Option<(String, String)> {
    use std::io::{BufRead, BufReader};
    let file = fs::File::open(path.join("logs/latest.log")).ok()?;
    use std::io::Read;
    for line in BufReader::new(file.take(64 * 1024))
        .lines()
        .take(100)
        .map_while(std::result::Result::ok)
    {
        if let Some((_, game)) = line.split_once("Loading Minecraft ") {
            let (version, loader) = game.split_once(" with ")?;
            if loader.starts_with("Fabric Loader") {
                return Some((version.to_owned(), "fabric".into()));
            }
        }
    }
    None
}

fn candidate(
    launcher: &str,
    key: &str,
    path: PathBuf,
    name: &str,
    version: &str,
    loader: &str,
    total_ms: u64,
    last_played: Option<u64>,
) -> Option<Candidate> {
    if !path.is_dir() {
        return None;
    }
    let (version, loader) = if version.is_empty() {
        log_version(&path).unwrap_or_else(|| (String::new(), loader.to_owned()))
    } else {
        (version.to_owned(), loader.to_ascii_lowercase())
    };
    let id = format!(
        "{:x}",
        Sha256::digest(format!("{launcher}\0{key}\0{}", path.display()))
    );
    let playable = crate::game::supported_versions().any(|supported| supported == version)
        && matches!(loader.as_str(), "fabric" | "vanilla");
    Some(Candidate {
        profile: Profile {
            id,
            name: name.chars().take(100).collect(),
            version,
            loader,
            launcher: launcher.into(),
            total_ms,
            last_played,
            playable,
        },
        path,
        sessions: Vec::new(),
    })
}

fn official(root: &Path, candidates: &mut Vec<Candidate>) {
    for file in [
        "launcher_profiles.json",
        "launcher_profiles_microsoft_store.json",
    ] {
        let data = json(&root.join(file));
        let Some(profiles) = data["profiles"].as_object() else {
            continue;
        };
        for (key, value) in profiles {
            let directory = value["gameDir"]
                .as_str()
                .map(PathBuf::from)
                .unwrap_or_else(|| root.to_owned());
            let raw = text(value, &["lastVersionId"]);
            let version_meta = if matches!(
                Path::new(raw).components().collect::<Vec<_>>().as_slice(),
                [std::path::Component::Normal(_)]
            ) {
                json(&root.join("versions").join(raw).join(format!("{raw}.json")))
            } else {
                Value::Null
            };
            let inherited = text(&version_meta, &["inheritsFrom"]);
            let (version, loader) =
                version_hint(if inherited.is_empty() { raw } else { inherited });
            let (_, inferred_loader) = version_hint(raw);
            let name = value["name"]
                .as_str()
                .filter(|name| !name.is_empty())
                .unwrap_or(if raw.is_empty() { "Minecraft" } else { raw });
            candidates.extend(candidate(
                "Minecraft Launcher",
                key,
                directory,
                name,
                version,
                if inferred_loader == "vanilla" {
                    loader
                } else {
                    inferred_loader
                },
                0,
                None,
            ));
        }
    }
}

fn instances_with_metadata(launcher: &str, root: &Path, candidates: &mut Vec<Candidate>) {
    for path in folders(root) {
        let cfg = ini(&path.join("instance.cfg"));
        let mmc = json(&path.join("mmc-pack.json"));
        let curse = json(&path.join("minecraftinstance.json"));
        let at = json(&path.join("instance.json"));
        let legacy = json(&path.join("profile.json"));
        let metadata = legacy.get("metadata").unwrap_or(&legacy);
        let components = mmc["components"].as_array();
        let mc = components.and_then(|items| items.iter().find(|v| v["uid"] == "net.minecraft"));
        let loader = components
            .and_then(|items| {
                items.iter().find_map(|v| match v["uid"].as_str()? {
                    "net.fabricmc.fabric-loader" => Some("fabric"),
                    "net.minecraftforge" => Some("forge"),
                    "net.neoforged" => Some("neoforge"),
                    "org.quiltmc.quilt-loader" => Some("quilt"),
                    _ => None,
                })
            })
            .unwrap_or_else(|| {
                [
                    text(metadata, &["loader", "mod_loader"]),
                    text(&curse["baseModLoader"], &["name"]),
                    text(&at["launcher"]["loaderVersion"], &["type"]),
                ]
                .into_iter()
                .find(|v| !v.is_empty())
                .unwrap_or("vanilla")
            });
        let version = [
            mc.map(|v| text(v, &["version"])).unwrap_or_default(),
            text(metadata, &["game_version"]),
            text(&curse, &["minecraftVersion"]),
            text(&curse["baseModLoader"], &["minecraftVersion"]),
            text(&at, &["inheritsFrom", "id"]),
        ]
        .into_iter()
        .find(|v| !v.is_empty())
        .unwrap_or_default();
        let name = [
            cfg.get("name").map(String::as_str).unwrap_or_default(),
            text(metadata, &["name"]),
            text(&curse, &["name"]),
            text(&at["launcher"], &["name"]),
            named(&path),
        ]
        .into_iter()
        .find(|v| !v.is_empty())
        .unwrap_or("Minecraft");
        let game = [
            path.join(".minecraft"),
            path.join("minecraft"),
            path.clone(),
        ]
        .into_iter()
        .find(|dir| dir.is_dir())
        .unwrap_or_else(|| path.clone());
        if cfg.is_empty()
            && [&mmc, &curse, &at, &legacy].into_iter().all(Value::is_null)
            && !GAME_FILES.iter().any(|file| game.join(file).exists())
        {
            continue;
        }
        let total = cfg
            .get("totalTimePlayed")
            .or_else(|| cfg.get("TotalTimePlayed"))
            .and_then(|n| n.parse::<u64>().ok())
            .unwrap_or_else(|| {
                num(metadata, "submitted_time_played")
                    .saturating_add(num(metadata, "recent_time_played"))
            });
        let last = cfg
            .get("lastLaunchTime")
            .and_then(|n| n.parse().ok())
            .or_else(|| metadata["last_played"].as_u64())
            .or_else(|| at["launcher"]["lastPlayed"].as_u64())
            .and_then(clock_ms);
        let loader = loader.split('-').next().unwrap_or(loader);
        candidates.extend(candidate(
            launcher,
            named(&path),
            game,
            name,
            version,
            loader,
            total.saturating_mul(1000),
            last,
        ));
    }
}

fn modrinth(root: &Path, candidates: &mut Vec<Candidate>) {
    use rusqlite::{Connection, OpenFlags};
    let Ok(db) = Connection::open_with_flags(root.join("app.db"), OpenFlags::SQLITE_OPEN_READ_ONLY)
    else {
        instances_with_metadata("Modrinth", &root.join("profiles"), candidates);
        return;
    };
    let _ = db.busy_timeout(std::time::Duration::from_millis(300));
    let custom: Option<String> = db
        .query_row("SELECT custom_dir FROM settings LIMIT 1", [], |row| {
            row.get(0)
        })
        .ok()
        .flatten();
    let profile_root = custom
        .filter(|path| !path.is_empty())
        .map(PathBuf::from)
        .filter(|path| path.is_absolute() && path.join("profiles").is_dir())
        .unwrap_or_else(|| root.to_owned())
        .join("profiles");
    let query = "SELECT i.path, i.name, c.game_version, c.loader, i.submitted_time_played, i.recent_time_played, i.last_played
                 FROM instances i LEFT JOIN instance_content_sets c ON c.id=i.applied_content_set_id";
    let legacy = "SELECT path, name, game_version, loader, submitted_time_played, recent_time_played, last_played FROM profiles";
    let Ok(mut statement) = db.prepare(query).or_else(|_| db.prepare(legacy)) else {
        instances_with_metadata("Modrinth", &profile_root, candidates);
        return;
    };
    let Ok(rows) = statement.query_map([], |row| {
        let key: String = row.get(0)?;
        let name: String = row.get(1)?;
        let version: Option<String> = row.get(2)?;
        let loader: Option<String> = row.get(3)?;
        let submitted: Option<u64> = row.get(4)?;
        let recent: Option<u64> = row.get(5)?;
        let last: Option<u64> = row.get(6)?;
        Ok((
            key,
            name,
            version,
            loader,
            submitted
                .unwrap_or_default()
                .saturating_add(recent.unwrap_or_default()),
            last,
        ))
    }) else {
        return;
    };
    for (key, name, version, loader, seconds, last) in rows.flatten() {
        if !matches!(
            Path::new(&key).components().collect::<Vec<_>>().as_slice(),
            [std::path::Component::Normal(_)]
        ) {
            continue;
        }
        let mut path = profile_root.join(&key);
        if !path.is_dir() {
            path = root.join("profiles").join(&key);
        }
        candidates.extend(candidate(
            "Modrinth",
            &key,
            path,
            &name,
            version.as_deref().unwrap_or_default(),
            loader.as_deref().unwrap_or("vanilla"),
            seconds.saturating_mul(1000),
            last.and_then(clock_ms),
        ));
    }
}

fn log_interval(file: &Path) -> Option<(u64, u64)> {
    let name = file.file_name()?.to_str()?;
    let input = fs::File::open(file).ok()?;
    let modified: DateTime<Local> = input.metadata().ok()?.modified().ok()?.into();
    let (date, input): (_, Box<dyn Read>) = if name == "latest.log" {
        (modified.date_naive(), Box::new(input))
    } else {
        let (date, index) = name.strip_suffix(".log.gz")?.rsplit_once('-')?;
        index.parse::<u32>().ok()?;
        (NaiveDate::parse_from_str(date, "%Y-%m-%d").ok()?,
            Box::new(flate2::read::MultiGzDecoder::new(input)))
    };
    let mut stamps = BufReader::new(input.take(64 * 1024 * 1024))
        .split(b'\n')
        .filter_map(|line| {
            let line = line.ok()?;
            let prefix = line.strip_prefix(b"[")?;
            let prefix = std::str::from_utf8(prefix.get(..prefix.iter().position(|&b| b == b']')?)?).ok()?;
            NaiveTime::parse_from_str(prefix, "%H:%M:%S").ok()
                .map(|time| (date.and_time(time), false))
                .or_else(|| NaiveDateTime::parse_from_str(prefix, "%d%b%Y %H:%M:%S").ok().map(|time| (time, true)))
        });
    let (mut start, dated) = stamps.next()?;
    let (mut end, _) = stamps.last()?;
    if !dated && end < start {
        if name == "latest.log" {
            start = start.checked_sub_days(chrono::Days::new(1))?;
        } else {
            end = end.checked_add_days(chrono::Days::new(1))?;
        }
    }
    let start = Local.from_local_datetime(&start).earliest()?.timestamp_millis();
    let end = Local.from_local_datetime(&end).latest()?.timestamp_millis();
    (start > 0 && end - start >= 60_000).then_some((start as u64, end as u64))
}

fn import_modrinth_activity(candidate: &Candidate, target: &Path) -> Result<()> {
    let file = target.join(MODRINTH_ACTIVITY);
    if file.is_file() {
        return Ok(());
    }
    let mut intervals = BTreeMap::<u64, u64>::new();
    let logs = candidate.path.join("logs");
    if let Ok(entries) = fs::read_dir(logs) {
        for entry in entries.flatten() {
            if entry.file_type().is_ok_and(|kind| kind.is_file()) {
                if let Some((start, end)) = log_interval(&entry.path()) {
                    intervals.entry(start).and_modify(|known| *known = (*known).max(end)).or_insert(end);
                }
            }
        }
    }
    let profile = &candidate.profile;
    let sessions: Vec<_> = intervals.into_iter()
        .filter_map(|(start, end)| PlaySession::imported(&profile.name, &profile.version, start, end))
        .map(|session| session.with_imported_profile(&profile.id))
        .collect();
    crate::game::save_activity_file(&file, &sessions)
}

pub(crate) fn restore_modrinth_activity(profiles: &[(Profile, PathBuf)]) {
    let pending: BTreeMap<_, _> = profiles.iter()
        .filter(|(profile, path)| profile.launcher == "Modrinth" && !path.join(MODRINTH_ACTIVITY).exists())
        .map(|(profile, path)| (profile.id.as_str(), path))
        .collect();
    if pending.is_empty() {
        return;
    }
    if let Some(roaming) = std::env::var_os("APPDATA") {
        let mut candidates = Vec::new();
        modrinth(&PathBuf::from(roaming).join("ModrinthApp"), &mut candidates);
        for candidate in candidates {
            if let Some(target) = pending.get(candidate.profile.id.as_str()) {
                let _ = import_modrinth_activity(&candidate, target);
            }
        }
    }
}

fn aw(root: &Path, candidates: &mut Vec<Candidate>) {
    let history = json(&root.join("activity.json"));
    let builds = json(&root.join("local-builds.json"));
    for build in builds.as_array().into_iter().flatten() {
        let id = text(build, &["id"]);
        let name = text(build, &["name"]);
        let directory = text(build, &["directoryName"]);
        if id.is_empty()
            || !matches!(
                Path::new(directory)
                    .components()
                    .collect::<Vec<_>>()
                    .as_slice(),
                [std::path::Component::Normal(_)]
            )
        {
            continue;
        }
        let Some(mut found) = candidate(
            "AWLauncher",
            id,
            root.join("instances/builds").join(directory),
            name,
            text(build, &["versionId"]),
            text(build, &["loader"]),
            0,
            None,
        ) else {
            continue;
        };
        found.sessions = history
            .as_array()
            .into_iter()
            .flatten()
            .filter(|session| {
                session["buildId"] == id
                    || (session["buildId"].is_null() && session["instance"] == directory)
            })
            .filter_map(|session| {
                PlaySession::imported(
                    name,
                    &found.profile.version,
                    num(session, "start"),
                    num(session, "end"),
                )
            })
            .collect();
        candidates.push(found);
    }
    for path in folders(&root.join("instances")) {
        let key = named(&path);
        if matches!(key, "builds" | "packs") {
            continue;
        }
        let (version, loader) = version_hint(key);
        let sessions: Vec<_> = history
            .as_array()
            .into_iter()
            .flatten()
            .filter(|session| session["buildId"].is_null() && session["instance"] == key)
            .filter_map(|session| {
                PlaySession::imported(
                    key,
                    text(session, &["versionId"]),
                    num(session, "start"),
                    num(session, "end"),
                )
            })
            .collect();
        if sessions.is_empty() && !GAME_FILES.iter().any(|file| path.join(file).exists()) {
            continue;
        }
        if let Some(mut found) = candidate(
            "AWLauncher",
            key,
            path.clone(),
            key,
            version,
            loader,
            0,
            None,
        ) {
            found.sessions = sessions;
            candidates.push(found);
        }
    }
}

fn scan(folder: Option<&str>) -> Vec<Candidate> {
    let mut candidates = Vec::new();
    let home = std::env::var_os("USERPROFILE").map(PathBuf::from);
    if let Some(roaming) = std::env::var_os("APPDATA").map(PathBuf::from) {
        official(&roaming.join(".minecraft"), &mut candidates);
        modrinth(&roaming.join("ModrinthApp"), &mut candidates);
        for (launcher, folder, config) in [
            ("Prism Launcher", "PrismLauncher", "prismlauncher.cfg"),
            ("MultiMC", "MultiMC", "multimc.cfg"),
            ("PolyMC", "PolyMC", "polymc.cfg"),
        ] {
            let root = roaming.join(folder);
            let base = ini(&root.join(config))
                .get("InstanceDir")
                .map(|path| root.join(path))
                .unwrap_or_else(|| root.join("instances"));
            instances_with_metadata(launcher, &base, &mut candidates);
        }
        instances_with_metadata(
            "ATLauncher",
            &roaming.join("ATLauncher/instances"),
            &mut candidates,
        );
        let horizon = roaming.join("horizon-launcher");
        let settings = json(&horizon.join("settings.json"));
        let directory = text(&settings, &["minecraft_dir"]);
        if !directory.is_empty() {
            let history = json(&horizon.join("instances.json"));
            let record = &history["instances"]["horizon"];
            let path = PathBuf::from(directory);
            let (version, loader) = log_version(&path)
                .or_else(|| {
                    candidates
                        .iter()
                        .find(|c| c.path == path)
                        .map(|c| (c.profile.version.clone(), c.profile.loader.clone()))
                })
                .unwrap_or_default();
            candidates.extend(candidate(
                "Horizon",
                "horizon",
                path,
                "Horizon",
                &version,
                &loader,
                num(record, "played_secs").saturating_mul(1000),
                clock_ms(num(record, "last_played")),
            ));
        }
        for (launcher, folder) in [
            ("GDLauncher", "gdlauncher_next"),
            ("GDLauncher", "gdlauncher"),
        ] {
            instances_with_metadata(
                launcher,
                &roaming.join(folder).join("instances"),
                &mut candidates,
            );
        }
    }
    if let Some(home) = home {
        aw(&home.join(".aw"), &mut candidates);
        instances_with_metadata(
            "ATLauncher",
            &home.join("ATLauncher/instances"),
            &mut candidates,
        );
        for root in [
            home.join("curseforge/minecraft/Instances"),
            home.join("Twitch/Minecraft/Instances"),
        ] {
            instances_with_metadata("CurseForge", &root, &mut candidates);
        }
    }
    #[cfg(windows)]
    if let Some(documents) = dirs::document_dir() {
        instances_with_metadata(
            "CurseForge",
            &documents.join("Curse/Minecraft/Instances"),
            &mut candidates,
        );
    }
    if let Some(folder) = folder.filter(|folder| !folder.trim().is_empty()) {
        let path = PathBuf::from(folder.trim().trim_matches('"'));
        if path.is_absolute() && path.is_dir() {
            if path.join("app.db").is_file() {
                modrinth(&path, &mut candidates);
            }
            if path.join("launcher_profiles.json").is_file() {
                official(&path, &mut candidates);
            }
            if let Some(parent) = path.parent() {
                let mut siblings = Vec::new();
                instances_with_metadata("Minecraft", parent, &mut siblings);
                candidates.extend(siblings.into_iter().filter(|profile| {
                    profile.path == path || profile.path.parent() == Some(path.as_path())
                }));
            }
            for base in [path.join("instances"), path.join("profiles"), path] {
                instances_with_metadata("Minecraft", &base, &mut candidates);
            }
        }
    }
    candidates.sort_by(|a, b| {
        (&a.profile.launcher, &a.profile.name, &a.profile.id).cmp(&(
            &b.profile.launcher,
            &b.profile.name,
            &b.profile.id,
        ))
    });
    let mut ids = std::collections::HashSet::new();
    candidates.retain(|candidate| ids.insert(candidate.profile.id.clone()));
    candidates
}

fn safe_id(id: &str) -> bool {
    id.len() == 64 && id.bytes().all(|b| b.is_ascii_hexdigit())
}

pub(crate) fn stored_profiles(root: &Path) -> impl Iterator<Item = (Profile, PathBuf)> {
    folders(&root.join("imported"))
        .filter_map(|path| {
            let profile: Profile = serde_json::from_value(json(&path.join("profile.json"))).ok()?;
            (safe_id(&profile.id) && named(&path) == profile.id).then_some((profile, path))
        })
}

pub(crate) fn installed(app: &tauri::AppHandle) -> Result<Vec<(Profile, PathBuf)>> {
    Ok(stored_profiles(&instances(app)?).collect())
}

pub(crate) fn instance(app: &tauri::AppHandle, version: &str, id: Option<&str>) -> Result<PathBuf> {
    let Some(id) = id else {
        return crate::catalog::instance_dir(app, version);
    };
    installed(app)?
        .into_iter()
        .find(|(profile, _)| profile.id == id && profile.version == version && profile.playable())
        .map(|(_, path)| path)
        .ok_or_else(|| "game_version".into())
}

#[tauri::command]
pub async fn imported_profiles(app: tauri::AppHandle) -> Result<Vec<Profile>> {
    tauri::async_runtime::spawn_blocking(move || {
        Ok(installed(&app)?
            .into_iter()
            .map(|(profile, _)| profile)
            .collect())
    })
    .await
    .map_err(|_| "game_storage".to_owned())?
}

#[tauri::command]
pub async fn create_profile(app: tauri::AppHandle, name: String, version: String) -> Result<Profile> {
    tauri::async_runtime::spawn_blocking(move || {
        create_local_profile(&instances(&app)?.join("imported"), &name, version)
    })
    .await
    .map_err(|_| "game_storage".to_owned())?
}

fn create_local_profile(root: &Path, name: &str, version: String) -> Result<Profile> {
    let name = name.trim();
    if name.is_empty() || name.chars().count() > 100 || name.chars().any(char::is_control) {
        return Err("profile_name".into());
    }
    if !crate::game::supported_versions().any(|supported| supported == version) {
        return Err("game_version".into());
    }
    fs::create_dir_all(root).map_err(|_| "game_storage")?;
    let staging = tempfile::tempdir_in(root).map_err(|_| "game_storage")?;
    let profile = Profile {
        id: format!("{:x}", Sha256::digest(uuid::Uuid::new_v4().as_bytes())),
        name: name.into(),
        version,
        loader: "fabric".into(),
        launcher: "Gleam Launcher".into(),
        total_ms: 0,
        last_played: None,
        playable: true,
    };
    save_profile(staging.path(), &profile)?;
    fs::rename(staging.path(), root.join(&profile.id)).map_err(|_| "game_storage")?;
    Ok(profile)
}

fn save_profile(directory: &Path, profile: &Profile) -> Result<()> {
    fs::write(
        directory.join("profile.json"),
        serde_json::to_vec(profile).map_err(|_| "game_storage")?,
    )
    .map_err(|_| "game_storage".into())
}

#[tauri::command]
pub async fn discover_profiles(
    app: tauri::AppHandle,
    first_login: bool,
    folder: Option<String>,
) -> Result<Discovery> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = instances(&app)?;
        let onboarding = !root.join(".import-reviewed").exists();
        if first_login && !onboarding {
            return Ok(Discovery {
                profiles: vec![],
                onboarding,
            });
        }
        let profiles = scan(folder.as_deref())
            .into_iter()
            .map(|candidate| {
                let target = root.join("imported").join(&candidate.profile.id);
                let imported = target.join("profile.json").is_file();
                if imported && candidate.profile.launcher == "Modrinth" {
                    let _ = import_modrinth_activity(&candidate, &target);
                }
                Offer {
                    path: candidate.path.to_string_lossy().into_owned(),
                    imported,
                    sessions: candidate.sessions.len(),
                    profile: candidate.profile,
                }
            })
            .collect();
        Ok(Discovery {
            profiles,
            onboarding,
        })
    })
    .await
    .map_err(|_| "game_storage".to_owned())?
}

#[tauri::command]
pub fn dismiss_profile_import(app: tauri::AppHandle) -> Result<()> {
    let root = instances(&app)?;
    fs::create_dir_all(&root)
        .and_then(|_| fs::write(root.join(".import-reviewed"), []))
        .map_err(|_| "game_storage".into())
}

fn copy(source: &Path, destination: &Path) -> io::Result<()> {
    let metadata = match fs::symlink_metadata(source) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error),
    };
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        if metadata.file_attributes() & 0x400 != 0 {
            return Ok(());
        }
    }
    if metadata.is_symlink() {
        return Ok(());
    }
    let name = named(source).to_ascii_lowercase();
    if name.contains("account")
        || name.contains("session")
        || name.contains("token")
        || name.contains("credential")
        || name.contains("secret")
        || name.contains("password")
        || name == "options.txt.bak"
        || matches!(
            name.as_str(),
            "orion-client.json" | "session.lock" | "gleamclient.jar" | "gleam-fabric-api.jar"
        )
        || name.starts_with("gleam-optimizer-")
    {
        return Ok(());
    }
    if metadata.is_dir() {
        fs::create_dir(destination)?;
        for entry in fs::read_dir(source)? {
            let entry = entry?;
            copy(&entry.path(), &destination.join(entry.file_name()))?;
        }
    } else if metadata.is_file() {
        fs::copy(source, destination)?;
    }
    Ok(())
}

fn import_one(root: &Path, candidate: &Candidate, copy_mods: bool) -> Result<()> {
    let target = root.join(&candidate.profile.id);
    if target.join("profile.json").is_file() {
        return Ok(());
    }
    #[cfg(windows)]
    crate::game::check_storage_idle(&candidate.path).map_err(|_| "game_busy")?;
    let staging = tempfile::tempdir_in(root).map_err(|_| "game_storage")?;
    for file in GAME_FILES {
        if matches!(*file, "mods" | "config" | "defaultconfigs") && !copy_mods {
            continue;
        }
        copy(&candidate.path.join(file), &staging.path().join(file)).map_err(|_| "game_storage")?;
    }
    save_profile(staging.path(), &candidate.profile)?;
    if !candidate.sessions.is_empty() {
        crate::game::save_activity(staging.path(), &candidate.sessions)?;
    }
    if candidate.profile.launcher == "Modrinth" {
        import_modrinth_activity(candidate, staging.path())?;
    }
    match fs::rename(staging.path(), &target) {
        Ok(()) => Ok(()),
        Err(_) if target.join("profile.json").is_file() => Ok(()),
        Err(_) => Err("game_storage".into()),
    }
}

#[tauri::command]
pub async fn import_profiles(
    app: tauri::AppHandle,
    ids: Vec<String>,
    copy_mods: bool,
    folder: Option<String>,
) -> Result<ImportResult> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = instances(&app)?.join("imported");
        fs::create_dir_all(&root).map_err(|_| "game_storage")?;
        let mut result = ImportResult {
            imported: Vec::new(),
            failed: Vec::new(),
        };
        for candidate in scan(folder.as_deref())
            .into_iter()
            .filter(|candidate| ids.contains(&candidate.profile.id))
        {
            if import_one(&root, &candidate, copy_mods).is_ok() {
                result.imported.push(candidate.profile.id);
            } else {
                result.failed.push(candidate.profile.name);
            }
        }
        if result.imported.len() + result.failed.len() != ids.len() {
            result.failed.push("Профиль больше недоступен".into());
        }
        Ok(result)
    })
    .await
    .map_err(|_| "game_storage".to_owned())?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn creates_independent_builds_only_for_supported_versions() {
        let root = tempfile::tempdir().unwrap();
        let imports = root.path().join("imported");
        assert!(create_local_profile(&imports, "Legacy", "1.16.5".into()).is_err());
        assert!(create_local_profile(&imports, "  ", "26.2".into()).is_err());
        let first = create_local_profile(&imports, " Sandbox ", "26.2".into()).unwrap();
        let second = create_local_profile(&imports, "Sandbox", "26.2".into()).unwrap();
        assert_ne!(first.id, second.id);
        assert_eq!(first.name, "Sandbox");
        assert_eq!(first.loader, "fabric");
        assert!(first.playable());
        assert_eq!(stored_profiles(root.path()).count(), 2);
    }

    #[test]
    fn imports_aw_profile_history_once_and_leaves_credentials_at_source() {
        let root = tempfile::tempdir().unwrap();
        let source = root.path().join("instances/builds/Test");
        fs::create_dir_all(source.join("saves/World")).unwrap();
        fs::create_dir_all(source.join("config")).unwrap();
        fs::write(source.join("saves/World/level.dat"), b"world").unwrap();
        fs::write(source.join("config/launcher-accounts.json"), b"private").unwrap();
        fs::write(root.path().join("local-builds.json"), r#"[{"id":"test","name":"Test","versionId":"26.2","loader":"FABRIC","directoryName":"Test"}]"#).unwrap();
        fs::write(
            root.path().join("activity.json"),
            r#"[{"buildId":"test","start":1000,"end":3000}]"#,
        )
        .unwrap();
        let mut profiles = Vec::new();
        aw(root.path(), &mut profiles);
        assert_eq!(profiles.len(), 1);
        assert_eq!(profiles[0].sessions.len(), 1);
        assert!(profiles[0].profile.playable());
        let imports = root.path().join("imports");
        fs::create_dir(&imports).unwrap();
        import_one(&imports, &profiles[0], true).unwrap();
        import_one(&imports, &profiles[0], true).unwrap();
        let installed = imports.join(&profiles[0].profile.id);
        assert_eq!(
            fs::read(installed.join("saves/World/level.dat")).unwrap(),
            b"world"
        );
        assert!(installed.join("activity.json").is_file());
        assert!(!installed.join("config/launcher-accounts.json").exists());
        assert!(source.join("config/launcher-accounts.json").is_file());
        assert_eq!(folders(&imports).count(), 1);
    }

    #[test]
    fn reads_modrinth_profile_and_playtime_from_current_database() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir_all(root.path().join("profiles/Test")).unwrap();
        let database = rusqlite::Connection::open(root.path().join("app.db")).unwrap();
        database.execute_batch("
            CREATE TABLE settings (custom_dir TEXT);
            CREATE TABLE instances (path TEXT, name TEXT, applied_content_set_id INTEGER, submitted_time_played INTEGER, recent_time_played INTEGER, last_played INTEGER);
            CREATE TABLE instance_content_sets (id INTEGER, game_version TEXT, loader TEXT);
            INSERT INTO settings VALUES (NULL);
            INSERT INTO instance_content_sets VALUES (1, '26.1.2', 'fabric');
            INSERT INTO instances VALUES ('Test', 'Test', 1, 120, 30, 1700000000);
        ").unwrap();
        let mut profiles = Vec::new();
        modrinth(root.path(), &mut profiles);
        assert_eq!(profiles.len(), 1);
        assert_eq!(profiles[0].profile.total_ms, 150_000);
        assert_eq!(profiles[0].profile.last_played, Some(1_700_000_000_000));
        assert!(profiles[0].sessions.is_empty());
        let logs = profiles[0].path.join("logs");
        fs::create_dir(&logs).unwrap();
        let mut archived = flate2::write::GzEncoder::new(
            fs::File::create(logs.join("2023-11-13-1.log.gz")).unwrap(),
            flate2::Compression::default(),
        );
        use io::Write;
        archived.write_all(b"[23:55:00] start\n[00:05:00] end\n").unwrap();
        archived.finish().unwrap();
        let imports = tempfile::tempdir().unwrap();
        import_one(imports.path(), &profiles[0], false).unwrap();
        let target = imports.path().join(&profiles[0].profile.id);
        let history = json(&target.join(MODRINTH_ACTIVITY));
        let session = &history.as_array().unwrap()[0];
        assert_eq!(session["importedProfileId"], profiles[0].profile.id);
        assert_eq!(num(session, "endedAt") - num(session, "startedAt"), 600_000);
        assert!(!target.join("activity.json").exists());
        import_modrinth_activity(&profiles[0], &target).unwrap();
        assert_eq!(json(&target.join(MODRINTH_ACTIVITY)), history);
    }
}
