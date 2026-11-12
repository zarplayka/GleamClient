//! Installation and launch of the client's Fabric profile.
use crate::{minecraft::{GameSession, Minecraft}, settings_sync::SettingsSync};
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::{
    collections::HashMap,
    fs::{self, File, OpenOptions},
    io::{self, Read, Write},
    path::{Component, Path, PathBuf},
    process::{Command, Stdio},
    sync::{
        atomic::{AtomicBool, AtomicUsize, Ordering::Relaxed},
        Arc, Mutex,
    },
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tauri::{Emitter, Manager};

pub(crate) const VERSION: &str = "26.3";
const VERSION_INDEX: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const JAVA_INDEX: &str = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
const FABRIC_META: &str = "https://meta.fabricmc.net/v2/versions/loader";
const FABRIC_MAVEN: &str = "https://maven.fabricmc.net/";
const MODRINTH_CDN: &str = "https://cdn.modrinth.com/data/";
const GLEAM_CLIENT: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/gleamclient.jar"));
// These artifacts are the fallback for an unavailable Modrinth API.
const FABRIC_API: (&str, &str, u64) = (
    "P7dR8mSH/versions/998R2rNP/fabric-api-0.166.0%2B26.3.jar",
    "7f16cfcd4ef3d225a21417907634957787b31a5f",
    1797522,
);
const OPTIMIZERS: [(&str, &str, u64); 4] = [
    (
        "AANobbMI/versions/2MZWfUts/sodium-fabric-0.9.3-beta.4%2Bmc26.3.jar",
        "4c14da0cc83490269c3759b64329adba112c9a01",
        1913034,
    ),
    (
        "gvQqBUqZ/versions/fWv5bnZk/lithium-fabric-0.26.3%2Bmc26.3.jar",
        "2d3afe9d43bb2549e23479d8d93e644ff366bebf",
        919892,
    ),
    (
        "NNAgCjsB/versions/X1g3VhTx/entityculling-fabric-1.11.3-mc26.3.jar",
        "45f47f7a067f5efd733a32767dc3d1d770678b2a",
        1508547,
    ),
    (
        "5ZwdcRci/versions/3MP9UR23/ImmediatelyFast-Fabric-1.17.1%2B26.3.jar",
        "f565bbeb201f2305e81761429d3f1f47e1c3a321",
        99586,
    ),
];
type Result<T> = std::result::Result<T, String>;

#[derive(Clone, Default)]
pub struct Game {
    status: Arc<Mutex<Status>>,
}

#[derive(Default)]
enum Status {
    #[default]
    Idle,
    Preparing(Arc<AtomicBool>),
    Running { settings_sync: bool },
    Finishing,
}

struct ActiveGame {
    game: Game,
    app: tauri::AppHandle,
}

impl Game {
    pub(crate) fn is_syncing_settings(&self) -> bool {
        self.status.lock().is_ok_and(|status| matches!(*status, Status::Running { settings_sync: true }))
    }
}

impl Drop for ActiveGame {
    fn drop(&mut self) {
        if let Ok(mut status) = self.game.status.lock() {
            *status = Status::Idle;
        }
        if self.app.webview_windows().is_empty() {
            self.app.exit(0);
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchOptions {
    version: String,
    account: String,
    memory: u32,
    width: u32,
    height: u32,
    fullscreen: bool,
    jvm_arguments: String,
    fabric_loader: String,
    #[serde(default)]
    install_optimizers: bool,
}

#[derive(Clone, Deserialize, Serialize, PartialEq, Eq)]
struct Download {
    url: String,
    sha1: String,
    size: Option<u64>,
}

fn bundled_mod(directory: &Path, (path, sha1, size): (&str, &str, u64)) -> (Download, PathBuf) {
    let name = path.rsplit('/').next().unwrap().replace("%2B", "+");
    (
        Download {
            url: format!("{MODRINTH_CDN}{path}"),
            sha1: sha1.into(),
            size: Some(size),
        },
        directory.join(name),
    )
}

fn valid_optimizer_file(file: &Download, project: &str) -> bool {
    valid_hash(&file.sha1)
        && file.size.is_some_and(|size| (1..=64 * 1024 * 1024).contains(&size))
        && file.url.starts_with(&format!("{MODRINTH_CDN}{project}/versions/"))
        && file.url.ends_with(".jar")
        && reqwest::Url::parse(&file.url).is_ok_and(|url| {
            url.host_str() == Some("cdn.modrinth.com")
                && url.path().starts_with(&format!("/data/{project}/versions/"))
                && url.query().is_none()
                && url.fragment().is_none()
        })
}

fn select_optimizer_release(releases: &[Value], project: &str) -> Option<Download> {
    releases.iter().filter_map(|release| {
        if release["project_id"] != project
            || !matches!(release["version_type"].as_str(), Some("release" | "beta"))
            || release["status"] != "listed"
            || !release["game_versions"].as_array()?.iter().any(|version| version == VERSION)
            || !release["loaders"].as_array()?.iter().any(|loader| loader == "fabric")
            || release["dependencies"].as_array()?.iter().any(|dependency| {
                dependency["dependency_type"] == "required"
                    && (dependency["project_id"] != "P7dR8mSH"
                        || (!dependency["version_id"].is_null()
                            && dependency["version_id"] != "998R2rNP"))
            })
        {
            return None;
        }
        let published = release["date_published"].as_str()?;
        let files = release["files"].as_array()?;
        let file = files.iter().find(|file| file["primary"] == true).or_else(|| files.first())?;
        let candidate = Download {
            url: file["url"].as_str()?.into(),
            sha1: file["hashes"]["sha1"].as_str()?.to_ascii_lowercase(),
            size: file["size"].as_u64(),
        };
        valid_optimizer_file(&candidate, project).then_some((published, candidate))
    }).max_by_key(|(published, _)| *published).map(|(_, file)| file)
}

#[cfg(test)]
#[test]
fn chooses_the_newest_compatible_optimizer_release() {
    let project = "AANobbMI";
    let valid = json!({
        "project_id": project, "version_type": "beta", "status": "listed", "date_published": "2026-11-11T00:00:00Z",
        "game_versions": [VERSION], "loaders": ["fabric"],
        "dependencies": [{"dependency_type": "required", "project_id": "P7dR8mSH", "version_id": null}],
        "files": [{"primary": true, "url": "https://cdn.modrinth.com/data/AANobbMI/versions/release/optimizer.jar",
            "hashes": {"sha1": "33ea0f572a6afa3ba71bb17fe3ef44358c8293f9"}, "size": 1024}]
    });
    let mut incompatible = valid.clone();
    incompatible["dependencies"][0]["project_id"] = json!("unknown");
    assert!(select_optimizer_release(std::slice::from_ref(&incompatible), project).is_none());
    let mut wrong_loader = valid.clone();
    wrong_loader["loaders"] = json!(["neoforge"]);
    assert!(select_optimizer_release(std::slice::from_ref(&wrong_loader), project).is_none());
    let mut older = valid.clone();
    older["date_published"] = json!("2026-11-10T00:00:00Z");
    older["files"][0]["size"] = json!(512);
    let selected = select_optimizer_release(&[older,wrong_loader,incompatible,valid], project).unwrap();
    assert_eq!(selected.size, Some(1024));
}

#[derive(Deserialize, Serialize)]
struct LaunchMetadata {
    resolved_at: SystemTime,
    version: Value,
    java_manifest: Value,
    fabric: Value,
}

impl LaunchMetadata {
    fn read(path: &Path) -> Option<Self> {
        serde_json::from_reader(File::open(path).ok()?).ok()
    }

    fn save(&self, path: &Path) {
        if let Some(parent) = path.parent() {
            if let Ok(mut output) = tempfile::NamedTempFile::new_in(parent) {
                if serde_json::to_writer(output.as_file_mut(), self).is_ok() {
                    let _ = output.persist(path);
                }
            }
        }
    }

    fn needs_refresh(&self) -> bool {
        self.resolved_at
            .elapsed()
            .is_ok_and(|age| age >= Duration::from_secs(24 * 60 * 60))
    }
}

#[derive(Deserialize, Serialize, PartialEq)]
struct VerifiedFile {
    sha1: String,
    size: u64,
    modified: SystemTime,
}

impl VerifiedFile {
    fn from_metadata(file: &Download, metadata: fs::Metadata) -> Option<Self> {
        if !metadata.is_file() || file.size.is_some_and(|size| size != metadata.len()) {
            return None;
        }
        Some(Self {
            sha1: file.sha1.clone(),
            size: metadata.len(),
            modified: metadata.modified().ok()?,
        })
    }
}

struct Installer<'a> {
    client: Client,
    cancelled: &'a AtomicBool,
    progress: &'a (dyn Fn(&str, usize) + Sync),
    verified: Mutex<HashMap<PathBuf, VerifiedFile>>,
    verification_changed: AtomicBool,
}

impl Installer<'_> {
    fn check(&self) -> Result<()> {
        if self.cancelled.load(Relaxed) {
            return Err("game_cancelled".into());
        }
        Ok(())
    }

    fn get(&self, url: &str) -> Result<reqwest::blocking::Response> {
        self.get_with_timeout(url, None)
    }

    fn get_with_timeout(&self, url: &str, timeout: Option<Duration>) -> Result<reqwest::blocking::Response> {
        self.check()?;
        let url = reqwest::Url::parse(url).map_err(|_| "game_metadata")?;
        if url.scheme() != "https"
            || !matches!(
                url.host_str(),
                Some(
                    "piston-meta.mojang.com"
                        | "piston-data.mojang.com"
                        | "libraries.minecraft.net"
                        | "resources.download.minecraft.net"
                        | "launcher.mojang.com"
                        | "meta.fabricmc.net"
                        | "maven.fabricmc.net"
                        | "cdn.modrinth.com"
                )
            )
        {
            return Err("game_metadata".into());
        }
        let request = self.client.get(url);
        let request = match timeout {
            Some(timeout) => request.timeout(timeout),
            None => request,
        };
        request
            .send()
            .and_then(|reply| reply.error_for_status())
            .map_err(|_| "game_download".into())
    }

    fn manifest(&self, metadata: &Value, path: &Path) -> Result<Value> {
        let file = download_info(metadata)?;
        self.download(&file, path, || {})?;
        serde_json::from_reader(File::open(path).map_err(io_error)?)
            .map_err(|_| "game_metadata".into())
    }

    fn download(&self, file: &Download, path: &Path, downloading: impl FnOnce()) -> Result<()> {
        self.download_with_timeout(file, path, downloading, None)
    }

    fn download_with_timeout(
        &self,
        file: &Download,
        path: &Path,
        downloading: impl FnOnce(),
        timeout: Option<Duration>,
    ) -> Result<()> {
        self.check()?;
        if !valid_hash(&file.sha1) {
            return Err("game_metadata".into());
        }
        let stamp = fs::metadata(path)
            .ok()
            .and_then(|metadata| VerifiedFile::from_metadata(file, metadata));
        if let Some(stamp) = &stamp {
            if self.verified.lock().map_err(|_| "game_install")?.get(path) == Some(stamp) {
                return Ok(());
            }
        }
        if let Ok(mut existing) = File::open(path) {
            if file
                .size
                .is_none_or(|size| existing.metadata().is_ok_and(|meta| meta.len() == size))
            {
                let mut digest = Sha1::new();
                io::copy(&mut existing, &mut digest).map_err(io_error)?;
                if format!("{:x}", digest.finalize()) == file.sha1 {
                    if let Some(stamp) = stamp {
                        let current = existing
                            .metadata()
                            .ok()
                            .and_then(|metadata| VerifiedFile::from_metadata(file, metadata));
                        if current.as_ref() == Some(&stamp) {
                            self.record_verification(path, stamp)?;
                        }
                    }
                    return Ok(());
                }
            }
        }
        let parent = path.parent().ok_or("game_storage")?;
        fs::create_dir_all(parent).map_err(io_error)?;
        downloading();
        let attempts = if timeout.is_some() { 1 } else { 3 };
        for attempt in 1..=attempts {
            match self.fetch(file, path, parent, timeout) {
                Err(error) if error == "game_download" && attempt < attempts => self.check()?,
                result => return result,
            }
        }
        Err("game_download".into())
    }

    fn record_verification(&self, path: &Path, stamp: VerifiedFile) -> Result<()> {
        self.verified
            .lock()
            .map_err(|_| "game_install")?
            .insert(path.to_owned(), stamp);
        self.verification_changed.store(true, Relaxed);
        Ok(())
    }

    fn save_verifications(&self, path: &Path) {
        if !self.verification_changed.load(Relaxed) {
            return;
        }
        // A missing receipt only means files will be verified again.
        if let (Ok(files), Some(parent)) = (self.verified.lock(), path.parent()) {
            if let Ok(mut output) = tempfile::NamedTempFile::new_in(parent) {
                if serde_json::to_writer(output.as_file_mut(), &*files).is_ok() {
                    let _ = output.persist(path);
                }
            }
        }
    }

    fn fetch(&self, file: &Download, path: &Path, parent: &Path, timeout: Option<Duration>) -> Result<()> {
        let mut response = self.get_with_timeout(&file.url, timeout)?;
        let mut output = tempfile::NamedTempFile::new_in(parent).map_err(io_error)?;
        let mut buffer = [0u8; 64 * 1024];
        let mut digest = Sha1::new();
        let mut size = 0u64;
        loop {
            self.check()?;
            let read = response.read(&mut buffer).map_err(|_| "game_download")?;
            if read == 0 {
                break;
            }
            size += read as u64;
            if file.size.is_some_and(|expected| size > expected) {
                return Err("game_integrity".into());
            }
            digest.update(&buffer[..read]);
            output.write_all(&buffer[..read]).map_err(io_error)?;
        }
        if file.size.is_some_and(|expected| size != expected)
            || format!("{:x}", digest.finalize()) != file.sha1
        {
            return Err("game_integrity".into());
        }
        drop(output.persist(path).map_err(|_| "game_storage")?);
        if let Some(stamp) = fs::metadata(path)
            .ok()
            .and_then(|metadata| VerifiedFile::from_metadata(file, metadata))
        {
            self.record_verification(path, stamp)?;
        }
        Ok(())
    }

    fn batch(
        &self,
        files: &[(Download, PathBuf)],
        stage: &str,
        start: usize,
        end: usize,
    ) -> Result<()> {
        (self.progress)("files", start);
        let next = AtomicUsize::new(0);
        let failed = AtomicBool::new(false);
        let completed = Mutex::new((0, false));
        std::thread::scope(|scope| {
            let workers: Vec<_> = (0..files.len().min(12))
                .map(|_| {
                    scope.spawn(|| -> Result<()> {
                        while !failed.load(Relaxed) {
                            let Some((file, path)) = files.get(next.fetch_add(1, Relaxed)) else {
                                break;
                            };
                            let downloading = || {
                                if let Ok(mut state) = completed.lock() {
                                    if !state.1 {
                                        state.1 = true;
                                        (self.progress)(
                                            stage,
                                            start + state.0 * (end - start) / files.len(),
                                        );
                                    }
                                }
                            };
                            if let Err(error) = self.download(file, path, downloading) {
                                failed.store(true, Relaxed);
                                return Err(error);
                            }
                            let mut state = completed.lock().map_err(|_| "game_install")?;
                            let before = state.0 * (end - start) / files.len();
                            state.0 += 1;
                            let after = state.0 * (end - start) / files.len();
                            if before != after {
                                (self.progress)(
                                    if state.1 { stage } else { "files" },
                                    start + after,
                                );
                            }
                        }
                        Ok(())
                    })
                })
                .collect();
            workers.into_iter().fold(Ok(()), |result, worker| {
                let finished = worker.join().unwrap_or_else(|_| Err("game_install".into()));
                result.and(finished)
            })
        })?;
        self.check()
    }

    fn resolve_metadata(&self, cache: &Path, loader: &str) -> Result<LaunchMetadata> {
        let versions: Value = self
            .get(VERSION_INDEX)?
            .json()
            .map_err(|_| "game_metadata")?;
        let entry = list(&versions["versions"])?
            .iter()
            .find(|entry| entry["id"] == VERSION)
            .ok_or("game_version")?;
        let version = self.manifest(
            entry,
            &cache.join("versions").join(format!("{VERSION}.json")),
        )?;
        if version["id"] != VERSION || version["javaVersion"]["majorVersion"] != 25 {
            return Err("game_metadata".into());
        }
        let component = string(&version["javaVersion"]["component"])?;
        let index: Value = self.get(JAVA_INDEX)?.json().map_err(|_| "game_metadata")?;
        let java_manifest = list(&index["windows-x64"][component])?
            .first()
            .and_then(|entry| entry.get("manifest"))
            .ok_or("game_metadata")?
            .clone();
        download_info(&java_manifest)?;
        Ok(LaunchMetadata {
            resolved_at: SystemTime::now(),
            version,
            java_manifest,
            fabric: self.resolve_fabric(loader)?,
        })
    }

    fn install(
        &self,
        game_dir: &Path,
        cache: &Path,
        options: &LaunchOptions,
        metadata: &LaunchMetadata,
    ) -> Result<Installation> {
        let version = &metadata.version;
        if version["id"] != VERSION || version["javaVersion"]["majorVersion"] != 25 {
            return Err("game_metadata".into());
        }
        let runtime = self.java(&version["javaVersion"], &metadata.java_manifest, cache)?;
        let mut files = Vec::new();
        let mut libraries = Vec::new();
        for lib in list(&version["libraries"])? {
            let name = string(&lib["name"])?;
            // The Windows x64 runtime uses the unqualified Windows native JARs.
            if !allowed(lib, false) || name.ends_with(":natives-windows-arm64") {
                continue;
            }
            let artifact = &lib["downloads"]["artifact"];
            let path = relative(&cache.join("libraries"), string(&artifact["path"])?)?;
            files.push((download_info(artifact)?, path.clone()));
            libraries.push(path);
        }
        let jar = cache.join("versions").join(format!("{VERSION}.jar"));
        files.push((download_info(&version["downloads"]["client"])?, jar.clone()));
        libraries.push(jar);
        let logging = &version["logging"]["client"];
        let log_config = relative(&cache.join("log-config"), string(&logging["file"]["id"])?)?;
        files.push((download_info(&logging["file"])?, log_config.clone()));
        self.batch(&files, "files", 25, 42)?;

        let assets = cache.join("assets");
        let index_meta = &version["assetIndex"];
        let asset_id = string(&index_meta["id"])?;
        let index_path = relative(&assets.join("indexes"), &format!("{asset_id}.json"))?;
        let index = self.manifest(index_meta, &index_path)?;
        let mut files = Vec::new();
        let mut hashes = std::collections::HashSet::new();
        for asset in index["objects"]
            .as_object()
            .ok_or("game_metadata")?
            .values()
        {
            let hash = string(&asset["hash"])?;
            if !valid_hash(hash) {
                return Err("game_metadata".into());
            }
            if hashes.insert(hash) {
                let object = format!("{}/{hash}", &hash[..2]);
                files.push((
                    Download {
                        url: format!("https://resources.download.minecraft.net/{object}"),
                        sha1: hash.to_owned(),
                        size: Some(asset["size"].as_u64().ok_or("game_metadata")?),
                    },
                    assets.join("objects").join(object),
                ));
            }
        }
        self.batch(&files, "assets", 42, 83)?;
        let mut fabric_libraries = self.fabric(cache, &metadata.fabric)?;
        fabric_libraries.extend(libraries);
        let managed_mods = self.managed_mods(game_dir, cache, options.install_optimizers)?;
        let natives = tempfile::Builder::new()
            .prefix("natives-")
            .tempdir_in(game_dir)
            .map_err(io_error)?;
        for name in ["java", "jna", "lwjgl", "netty"] {
            fs::create_dir_all(natives.path().join(name)).map_err(io_error)?;
        }
        let classpath = std::env::join_paths(fabric_libraries).map_err(|_| "game_storage")?;
        Ok(Installation {
            version: version.clone(),
            runtime,
            classpath,
            assets,
            natives,
            log_config,
            fabric: metadata.fabric.clone(),
            managed_mods,
        })
    }

    fn resolve_fabric(&self, requested: &str) -> Result<Value> {
        let loader = if requested == "auto" {
            let versions: Value = self
                .get(&format!("{FABRIC_META}/{VERSION}"))?
                .json()
                .map_err(|_| "game_fabric")?;
            list(&versions)?
                .iter()
                .find(|entry| entry["loader"]["stable"] == true)
                .and_then(|entry| entry["loader"]["version"].as_str())
                .ok_or("game_fabric")?
                .to_owned()
        } else {
            requested.to_owned()
        };
        if !valid_loader(&loader) {
            return Err("game_fabric".into());
        }
        let mut profile: Value = self
            .get(&format!("{FABRIC_META}/{VERSION}/{loader}/profile/json"))?
            .json()
            .map_err(|_| "game_fabric")?;
        if profile["inheritsFrom"] != VERSION
            || profile["mainClass"] != "net.fabricmc.loader.impl.launch.knot.KnotClient"
        {
            return Err("game_fabric".into());
        }
        for library in profile["libraries"].as_array_mut().ok_or("game_fabric")? {
            let url = Self::fabric_library_url(library)?;
            if library["sha1"].as_str().is_none() {
                let checksum = self
                    .get(&format!("{url}.sha1"))?
                    .text()
                    .map_err(|_| "game_fabric")?;
                library["sha1"] = json!(checksum.trim());
            }
            if !valid_hash(string(&library["sha1"])?) {
                return Err("game_fabric".into());
            }
        }
        Ok(profile)
    }

    fn fabric_library_url(library: &Value) -> Result<String> {
        let name = string(&library["name"])?;
        let parts: Vec<_> = name.split(':').collect();
        let [group, artifact, version] = parts.as_slice() else {
            return Err("game_fabric".into());
        };
        if library["url"] != FABRIC_MAVEN
            || parts.iter().any(|part| {
                part.is_empty()
                    || !part.bytes().all(|byte| {
                        byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-' | b'+')
                    })
            })
        {
            return Err("game_fabric".into());
        }
        Ok(format!(
            "{FABRIC_MAVEN}{}/{artifact}/{version}/{artifact}-{version}.jar",
            group.replace('.', "/")
        ))
    }

    fn fabric(&self, cache: &Path, profile: &Value) -> Result<Vec<PathBuf>> {
        if profile["inheritsFrom"] != VERSION
            || profile["mainClass"] != "net.fabricmc.loader.impl.launch.knot.KnotClient"
        {
            return Err("game_fabric".into());
        }
        let mut files = Vec::new();
        for library in list(&profile["libraries"])? {
            let url = Self::fabric_library_url(library)?;
            let path = relative(&cache.join("libraries"), &url[FABRIC_MAVEN.len()..])?;
            files.push((
                Download {
                    url,
                    sha1: string(&library["sha1"])?.to_owned(),
                    size: library["size"].as_u64(),
                },
                path,
            ));
        }
        self.batch(&files, "fabric", 83, 90)?;
        Ok(files.into_iter().map(|(_, path)| path).collect())
    }

    fn managed_mods(
        &self,
        game_dir: &Path,
        cache: &Path,
        install_optimizers: bool,
    ) -> Result<std::ffi::OsString> {
        let directory = cache.join("optimizers").join(VERSION);
        // Old launcher-owned artifact. Mods placed in game_dir/mods are separate.
        let _ = fs::remove_file(directory.join("zakoopt-2.1.2+26.3.jar"));
        let fabric_api = bundled_mod(&directory, FABRIC_API);
        self.batch(std::slice::from_ref(&fabric_api), "fabric", 90, 91)?;
        let mut paths = vec![fabric_api.1];
        if install_optimizers {
            paths.extend(self.update_optimizers(&directory)?);
        }
        let client = self.install_gleam_client(game_dir)?;
        paths.push(client);
        std::env::join_paths(paths).map_err(|_| "game_storage".into())
    }

    fn update_optimizers(&self, directory: &Path) -> Result<Vec<PathBuf>> {
        self.check()?;
        (self.progress)("optimizers", 91);
        let receipt = directory.join("optimizer-versions.json");
        let cached: HashMap<String, Download> = File::open(&receipt)
            .ok()
            .and_then(|file| serde_json::from_reader(file).ok())
            .unwrap_or_default();
        let selected = std::thread::scope(|scope| {
            let workers: Vec<_> = OPTIMIZERS.into_iter().map(|baseline| {
                let cached = &cached;
                scope.spawn(move || {
                    let project = baseline.0.split('/').next().unwrap();
                    let game_versions = format!(r#"["{VERSION}"]"#);
                    let releases = self.client
                        .get(format!("https://api.modrinth.com/v2/project/{project}/version"))
                        .query(&[("loaders", r#"["fabric"]"#), ("game_versions", game_versions.as_str()), ("include_changelog", "false")])
                        .timeout(Duration::from_secs(3))
                        .send().ok()
                        .and_then(|reply| reply.error_for_status().ok())
                        .and_then(|reply| reply.json::<Vec<Value>>().ok());
                    let latest = releases.as_deref().and_then(|releases| select_optimizer_release(releases, project));
                    let last_good = cached.get(project).filter(|file| valid_optimizer_file(file, project));
                    let (file, path) = bundled_mod(directory, baseline);
                    for candidate in latest.iter().chain(last_good) {
                        self.check()?;
                        if candidate.sha1 == file.sha1 {
                            break;
                        }
                        let candidate_path = directory.join(format!("{project}-{}.jar", candidate.sha1));
                        if self.download_with_timeout(candidate, &candidate_path, || {}, Some(Duration::from_secs(8))).is_ok() {
                            return Ok((project.to_owned(), candidate.clone(), candidate_path));
                        }
                    }
                    self.download(&file, &path, || {})?;
                    Ok::<_, String>((project.to_owned(), file, path))
                })
            }).collect();
            workers.into_iter().map(|worker| worker.join().map_err(|_| "game_install")?).collect::<Result<Vec<_>>>()
        })?;
        if cached.len() != selected.len() || selected.iter().any(|(project,file,_)| cached.get(project) != Some(file)) {
            let files: HashMap<_, _> = selected.iter().map(|(project,file,_)| (project,file)).collect();
            if let Ok(mut temporary) = tempfile::NamedTempFile::new_in(directory) {
                if serde_json::to_writer(&mut temporary, &files).is_ok() {
                    let _ = temporary.persist(receipt);
                }
            }
        }
        (self.progress)("optimizers", 95);
        Ok(selected.into_iter().map(|(_,_,path)| path).collect())
    }

    fn install_gleam_client(&self, game_dir: &Path) -> Result<PathBuf> {
        self.check()?;
        let directory = game_dir.join("gleam");
        let path = directory.join("gleamclient.jar");
        if let Ok(file) = File::open(&path) {
            if file
                .metadata()
                .is_ok_and(|metadata| metadata.len() == GLEAM_CLIENT.len() as u64)
            {
                let mut contents = Vec::with_capacity(GLEAM_CLIENT.len());
                file.take(GLEAM_CLIENT.len() as u64 + 1)
                    .read_to_end(&mut contents)
                    .map_err(io_error)?;
                if contents == GLEAM_CLIENT {
                    return Ok(path);
                }
            }
        }
        (self.progress)("gleam", 96);
        fs::create_dir_all(&directory).map_err(io_error)?;
        let mut staged = tempfile::NamedTempFile::new_in(&directory).map_err(io_error)?;
        staged.write_all(GLEAM_CLIENT).map_err(io_error)?;
        self.check()?;
        staged.persist(&path).map_err(|_| "game_storage")?;
        Ok(path)
    }

    fn java(&self, java: &Value, java_manifest: &Value, cache: &Path) -> Result<PathBuf> {
        (self.progress)("files", 2);
        let component = string(&java["component"])?;
        let root = relative(&cache.join("java"), component)?;
        let manifest = self.manifest(java_manifest, &root.join("manifest.json"))?;
        let mut files = Vec::new();
        for (name, entry) in manifest["files"].as_object().ok_or("game_metadata")? {
            let path = relative(&root, name)?;
            match string(&entry["type"])? {
                "directory" => fs::create_dir_all(path).map_err(io_error)?,
                "file" => files.push((download_info(&entry["downloads"]["raw"])?, path)),
                _ => return Err("game_metadata".into()),
            }
        }
        self.batch(&files, "java", 2, 25)?;
        Ok(root.join("bin/javaw.exe"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn installs_reuses_and_replaces_the_bundled_client() {
        let profile = tempfile::tempdir().unwrap();
        let cancelled = AtomicBool::new(false);
        let writes = AtomicUsize::new(0);
        let progress = |stage: &str, _: usize| {
            if stage == "gleam" {
                writes.fetch_add(1, Relaxed);
            }
        };
        let installer = Installer {
            client: Client::new(),
            cancelled: &cancelled,
            progress: &progress,
            verified: Mutex::new(HashMap::new()),
            verification_changed: AtomicBool::new(false),
        };
        let jar = installer.install_gleam_client(profile.path()).unwrap();
        assert_eq!(fs::read(&jar).unwrap(), GLEAM_CLIENT);
        assert_eq!(installer.install_gleam_client(profile.path()).unwrap(), jar);
        assert_eq!(writes.load(Relaxed), 1);

        let mut previous = GLEAM_CLIENT.to_vec();
        previous[0] ^= 1;
        fs::write(&jar, &previous).unwrap();
        cancelled.store(true, Relaxed);
        assert_eq!(
            installer.install_gleam_client(profile.path()).unwrap_err(),
            "game_cancelled"
        );
        assert_eq!(fs::read(&jar).unwrap(), previous);
        cancelled.store(false, Relaxed);
        installer.install_gleam_client(profile.path()).unwrap();
        assert_eq!(fs::read(jar).unwrap(), GLEAM_CLIENT);
        assert_eq!(writes.load(Relaxed), 2);
    }

    #[test]
    fn reuses_verified_files_and_detects_same_size_changes() {
        let cache = tempfile::tempdir().unwrap();
        let path = cache.path().join("javaw.exe");
        let receipt = cache.path().join("verified-files-v1.json");
        let file = Download {
            url: "https://example.invalid/javaw.exe".into(),
            sha1: format!("{:x}", Sha1::digest(b"good")),
            size: Some(4),
        };
        fs::write(&path, b"good").unwrap();
        let cancelled = AtomicBool::new(false);
        let events = Mutex::new(Vec::new());
        let progress =
            |stage: &str, percent| events.lock().unwrap().push((stage.to_owned(), percent));
        let client = Client::new();
        let make_installer = |verified| Installer {
            client: client.clone(),
            cancelled: &cancelled,
            progress: &progress,
            verified: Mutex::new(verified),
            verification_changed: AtomicBool::new(false),
        };
        let first = make_installer(HashMap::new());
        first
            .download(&file, &path, || panic!("cached file was downloaded"))
            .unwrap();
        first.save_verifications(&receipt);
        let verified = serde_json::from_reader(File::open(&receipt).unwrap()).unwrap();
        let next = make_installer(verified);
        let files = [(file, path.clone())];
        next.batch(&files, "java", 2, 25).unwrap();
        assert!(!next.verification_changed.load(Relaxed));
        assert!(events
            .lock()
            .unwrap()
            .iter()
            .all(|(stage, _)| stage == "files"));

        fs::write(&path, b"bad!").unwrap();
        File::options()
            .write(true)
            .open(&path)
            .unwrap()
            .set_modified(SystemTime::UNIX_EPOCH + Duration::from_secs(1_700_000_000))
            .unwrap();
        assert_eq!(
            next.batch(&files, "java", 2, 25).unwrap_err(),
            "game_metadata"
        );
        assert!(events
            .lock()
            .unwrap()
            .iter()
            .any(|(stage, _)| stage == "java"));
    }
}

struct Installation {
    version: Value,
    runtime: PathBuf,
    classpath: std::ffi::OsString,
    assets: PathBuf,
    natives: tempfile::TempDir,
    log_config: PathBuf,
    fabric: Value,
    managed_mods: std::ffi::OsString,
}

fn string(value: &Value) -> Result<&str> {
    value
        .as_str()
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "game_metadata".into())
}

fn list(value: &Value) -> Result<&[Value]> {
    value
        .as_array()
        .map(Vec::as_slice)
        .ok_or_else(|| "game_metadata".into())
}

fn download_info(value: &Value) -> Result<Download> {
    serde_json::from_value(value.clone()).map_err(|_| "game_metadata".into())
}

fn valid_hash(hash: &str) -> bool {
    hash.len() == 40 && hash.bytes().all(|c| c.is_ascii_hexdigit())
}

fn valid_loader(version: &str) -> bool {
    let mut parts = version.split('.');
    let [Some(major), Some(minor), Some(patch), None] =
        [parts.next(), parts.next(), parts.next(), parts.next()]
    else {
        return false;
    };
    [major, minor, patch].iter().all(|part| {
        !part.is_empty() && part.len() <= 3 && part.bytes().all(|byte| byte.is_ascii_digit())
    })
}

fn relative(root: &Path, path: &str) -> Result<PathBuf> {
    if path.contains([':', '\\']) {
        return Err("game_metadata".into());
    }
    let path = Path::new(path);
    if path.as_os_str().is_empty()
        || !path
            .components()
            .all(|part| matches!(part, Component::Normal(_)))
    {
        return Err("game_metadata".into());
    }
    Ok(root.join(path))
}

fn io_error(_: io::Error) -> String {
    "game_storage".into()
}

fn set_window_mode(game_dir: &Path, fullscreen: bool) -> Result<()> {
    let path = game_dir.join("options.txt");
    let current = match fs::read_to_string(&path) {
        Ok(text) => text,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(io_error(error)),
    };
    let mode = format!("fullscreen:{fullscreen}");
    if current.lines().any(|line| line == mode) {
        return Ok(());
    }
    let mut output = tempfile::NamedTempFile::new_in(game_dir).map_err(io_error)?;
    let mut needs_newline = false;
    for line in current
        .split_inclusive('\n')
        .filter(|line| !line.starts_with("fullscreen:"))
    {
        output.write_all(line.as_bytes()).map_err(io_error)?;
        needs_newline = !line.ends_with('\n');
    }
    let newline = if current.contains("\r\n") {
        "\r\n"
    } else {
        "\n"
    };
    if needs_newline {
        output.write_all(newline.as_bytes()).map_err(io_error)?;
    }
    write!(output, "{mode}{newline}").map_err(io_error)?;
    output.persist(path).map_err(|_| "game_storage")?;
    Ok(())
}

fn allowed(value: &Value, windowed: bool) -> bool {
    let Some(rules) = value["rules"].as_array() else {
        return true;
    };
    rules.iter().fold(false, |allowed, rule| {
        let os = &rule["os"];
        let platform = os["name"].as_str().is_none_or(|name| name == "windows")
            && os["arch"]
                .as_str()
                .is_none_or(|arch| matches!(arch, "x86_64" | "amd64"))
            && os["version"].is_null()
            && os["versionRange"].is_null();
        let features = rule["features"].as_object().is_none_or(|features| {
            features.iter().all(|(feature, required)| {
                required.as_bool() == Some(feature == "has_custom_resolution" && windowed)
            })
        });
        if platform && features {
            rule["action"] == "allow"
        } else {
            allowed
        }
    })
}

fn arguments(value: &Value, windowed: bool) -> Result<Vec<&str>> {
    let mut args = Vec::new();
    for entry in list(value)? {
        if let Some(arg) = entry.as_str() {
            args.push(arg);
        } else if allowed(entry, windowed) {
            if let Some(arg) = entry["value"].as_str() {
                args.push(arg);
            } else {
                for arg in list(&entry["value"])? {
                    args.push(string(arg)?);
                }
            }
        }
    }
    Ok(args)
}

// Same quoting behavior as the AWLauncher JVM argument field.
fn split_jvm_args(text: &str) -> Result<Vec<String>> {
    let mut result = Vec::new();
    let mut word = String::new();
    let mut quote = None;
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\\' if quote.is_some() && chars.peek().copied() == quote => {
                word.push(chars.next().unwrap());
            }
            '\'' | '"' if quote == Some(c) => quote = None,
            '\'' | '"' if quote.is_none() => quote = Some(c),
            c if c.is_whitespace() && quote.is_none() => {
                if !word.is_empty() {
                    result.push(std::mem::take(&mut word));
                }
            }
            _ => word.push(c),
        }
    }
    if quote.is_some() {
        return Err("game_jvm_quotes".into());
    }
    if !word.is_empty() {
        result.push(word);
    }
    Ok(result)
}

impl Installation {
    fn command(
        &self,
        game_dir: &Path,
        options: &LaunchOptions,
        session: &GameSession,
        extra: Vec<String>,
    ) -> Result<Command> {
        let mut java = Command::new(&self.runtime);
        java.current_dir(game_dir)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        let version = &self.version;
        let profile = &self.fabric;
        let values = [
            ("${auth_player_name}", session.name.as_str()),
            ("${auth_uuid}", session.uuid.as_str()),
            ("${auth_access_token}", session.token.as_str()),
            ("${version_name}", string(&profile["id"])?),
            (
                "${game_directory}",
                game_dir.to_str().ok_or("game_storage")?,
            ),
            (
                "${assets_root}",
                self.assets.to_str().ok_or("game_storage")?,
            ),
            (
                "${assets_index_name}",
                string(&version["assetIndex"]["id"])?,
            ),
            ("${version_type}", string(&version["type"])?),
            (
                "${natives_directory}",
                self.natives.path().to_str().ok_or("game_storage")?,
            ),
            ("${launcher_name}", "Gleam Launcher"),
            ("${launcher_version}", env!("CARGO_PKG_VERSION")),
            (
                "${classpath}",
                self.classpath.to_str().ok_or("game_storage")?,
            ),
            ("${clientid}", ""),
            ("${auth_xuid}", ""),
            ("${resolution_width}", &options.width.to_string()),
            ("${resolution_height}", &options.height.to_string()),
        ];
        let substitute = |arg: &str| -> Result<String> {
            let Some(start) = arg.find("${") else {
                return Ok(arg.to_owned());
            };
            let end = arg[start..].find('}').ok_or("game_metadata")? + start + 1;
            let value = values
                .iter()
                .find(|(key, _)| *key == &arg[start..end])
                .ok_or("game_metadata")?
                .1;
            Ok(format!("{}{value}{}", &arg[..start], &arg[end..]))
        };
        java.args(
            extra
                .into_iter()
                .filter(|arg| !arg.starts_with("-Xmx") && !arg.starts_with("-Xms")),
        );
        java.arg(format!("-Xmx{}G", options.memory));
        let log_argument = string(&version["logging"]["client"]["argument"])?;
        java.arg(log_argument.replace("${path}", self.log_config.to_str().ok_or("game_storage")?));
        for arg in arguments(&version["arguments"]["jvm"], !options.fullscreen)? {
            java.arg(substitute(arg)?);
        }
        for arg in arguments(&profile["arguments"]["jvm"], !options.fullscreen)? {
            java.arg(substitute(arg)?);
        }
        let mut mods_argument = std::ffi::OsString::from("-Dfabric.addMods=");
        mods_argument.push(&self.managed_mods);
        java.arg(mods_argument);
        java.arg(string(&profile["mainClass"])?);
        for arg in arguments(&version["arguments"]["game"], !options.fullscreen)? {
            java.arg(substitute(arg)?);
        }
        for arg in arguments(&profile["arguments"]["game"], !options.fullscreen)? {
            java.arg(substitute(arg)?);
        }
        if options.fullscreen {
            java.arg("--fullscreen");
        }
        Ok(java)
    }
}

#[tauri::command]
pub fn cancel_minecraft_install(game: tauri::State<'_, Game>) -> Result<()> {
    if let Status::Preparing(cancelled) = &*game.status.lock().map_err(|_| "game_install")? {
        cancelled.store(true, Relaxed);
    }
    Ok(())
}

#[cfg(windows)]
mod process {
    use super::*;
    use std::os::windows::io::{AsRawHandle, FromRawHandle, OwnedHandle, RawHandle};

    #[link(name = "kernel32")]
    extern "system" {
        fn OpenProcess(access: u32, inherit: i32, pid: u32) -> RawHandle;
        fn GetProcessTimes(
            process: RawHandle,
            created: *mut [u32; 2],
            exited: *mut [u32; 2],
            kernel: *mut [u32; 2],
            user: *mut [u32; 2],
        ) -> i32;
        fn WaitForSingleObject(handle: RawHandle, millis: u32) -> u32;
    }

    #[derive(Deserialize, Serialize)]
    struct Identity {
        pid: u32,
        created: [u32; 2],
    }

    pub(super) struct Running {
        pid: u32,
        handle: OwnedHandle,
    }

    fn creation_time(handle: RawHandle) -> Option<[u32; 2]> {
        let (mut created, mut exited, mut kernel, mut user) = ([0; 2], [0; 2], [0; 2], [0; 2]);
        let success = unsafe { GetProcessTimes(handle, &mut created, &mut exited, &mut kernel, &mut user) };
        (success != 0).then_some(created)
    }

    fn path(game_dir: &Path) -> PathBuf {
        game_dir.join(".gleam-process.json")
    }

    pub(super) fn save(game_dir: &Path, child: &std::process::Child) -> Result<()> {
        let identity = Identity {
            pid: child.id(),
            created: creation_time(child.as_raw_handle()).ok_or("game_spawn")?,
        };
        let mut temporary = tempfile::NamedTempFile::new_in(game_dir).map_err(io_error)?;
        serde_json::to_writer(&mut temporary, &identity).map_err(|_| "game_storage")?;
        temporary.persist(path(game_dir)).map_err(|error| io_error(error.error))?;
        Ok(())
    }

    pub(super) fn find(game_dir: &Path) -> Option<Running> {
        let identity: Identity = serde_json::from_reader(File::open(path(game_dir)).ok()?).ok()?;
        // SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION.
        let handle = unsafe { OpenProcess(0x0010_1000, 0, identity.pid) };
        if handle.is_null() {
            return None;
        }
        let running = Running {
            pid: identity.pid,
            handle: unsafe { OwnedHandle::from_raw_handle(handle) },
        };
        (creation_time(handle) == Some(identity.created) && running.alive()).then_some(running)
    }

    impl Running {
        pub(super) fn alive(&self) -> bool {
            const WAIT_TIMEOUT: u32 = 258;
            unsafe { WaitForSingleObject(self.handle.as_raw_handle(), 0) == WAIT_TIMEOUT }
        }

        pub(super) fn close_window(&self) -> bool {
            request_window_close(self.pid)
        }
    }

    #[cfg(test)]
    #[test]
    fn recovers_live_process_and_rejects_stale_identity() {
        use std::os::windows::process::CommandExt;

        let directory = tempfile::tempdir().unwrap();
        let mut child = Command::new("cmd.exe")
            .args(["/d", "/c", "pause"])
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .creation_flags(0x0800_0000) // CREATE_NO_WINDOW
            .spawn()
            .unwrap();
        let saved = save(directory.path(), &child);
        let recovered = find(directory.path());
        let incorrect = Identity { pid: child.id(), created: [0; 2] };
        let wrong_identity = serde_json::to_vec(&incorrect).unwrap();
        fs::write(path(directory.path()), wrong_identity).unwrap();
        let stale = find(directory.path());
        let _ = child.kill();
        let _ = child.wait();
        saved.unwrap();
        let recovered = recovered.unwrap();
        assert_eq!(recovered.pid, child.id());
        assert!(stale.is_none());
        assert!(!recovered.alive());
    }
}

#[tauri::command]
pub fn minecraft_running(app: tauri::AppHandle) -> Result<bool> {
    #[cfg(windows)]
    {
        let game_dir = crate::catalog::instance_dir(&app, VERSION)?;
        Ok(process::find(&game_dir).is_some())
    }
    #[cfg(not(windows))]
    {
        let _ = app;
        Ok(false)
    }
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaySession {
    player: String,
    version: String,
    started_at: u64,
    ended_at: Option<u64>,
}

fn timestamp(time: SystemTime) -> u64 {
    time.duration_since(UNIX_EPOCH).unwrap_or_default().as_millis() as u64
}

fn read_activity(game_dir: &Path) -> Result<Vec<PlaySession>> {
    match File::open(game_dir.join("activity.json")) {
        Ok(file) => serde_json::from_reader(file).map_err(|_| "game_storage".into()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Vec::new()),
        Err(error) => Err(io_error(error)),
    }
}

fn save_activity(game_dir: &Path, sessions: &[PlaySession]) -> Result<()> {
    let mut temporary = tempfile::NamedTempFile::new_in(game_dir).map_err(io_error)?;
    serde_json::to_writer(&mut temporary, sessions).map_err(|_| "game_storage")?;
    temporary.persist(game_dir.join("activity.json")).map_err(|error| io_error(error.error))?;
    Ok(())
}

fn recover_activity(game_dir: &Path, sessions: &mut [PlaySession]) {
    if let Some(session) = sessions.last_mut().filter(|session| session.ended_at.is_none()) {
        // A launcher closed before its child cannot observe the exit time. Use the
        // last game log write rather than counting time until the launcher reopens.
        let last_log = fs::metadata(game_dir.join("logs/latest.log"))
            .and_then(|file| file.modified())
            .map(timestamp)
            .unwrap_or(session.started_at);
        session.ended_at = Some(last_log.clamp(session.started_at, timestamp(SystemTime::now()).max(session.started_at)));
    }
}

#[cfg(test)]
#[test]
fn activity_recovers_exit_without_counting_launcher_downtime() {
    let profile = tempfile::tempdir().unwrap();
    let game_dir = profile.path();
    fs::create_dir(game_dir.join("logs")).unwrap();
    let start = SystemTime::now() - Duration::from_secs(3600);
    let end = start + Duration::from_secs(120);
    File::create(game_dir.join("logs/latest.log")).unwrap().set_modified(end).unwrap();
    save_activity(game_dir, &[PlaySession {
        player: "TestPlayer".into(),
        version: VERSION.into(),
        started_at: timestamp(start),
        ended_at: None,
    }]).unwrap();
    let mut history = read_activity(game_dir).unwrap();
    recover_activity(game_dir, &mut history);
    assert_eq!(history[0].ended_at, Some(timestamp(end)));
}

#[tauri::command]
pub async fn minecraft_activity(app: tauri::AppHandle) -> Result<Vec<PlaySession>> {
    tauri::async_runtime::spawn_blocking(move || {
        let game_dir = crate::catalog::instance_dir(&app, VERSION)?;
        let mut sessions = read_activity(&game_dir)?;
        if !minecraft_running(app)? {
            recover_activity(&game_dir, &mut sessions);
        }
        Ok(sessions)
    })
    .await
    .map_err(|_| "game_storage".to_owned())?
}

#[cfg(windows)]
pub(crate) fn check_storage_idle(game_dir: &Path) -> io::Result<()> {
    use std::os::windows::fs::OpenOptionsExt;

    let _lock = match OpenOptions::new().read(true).write(true).open(game_dir.join(".gleam-launch.lock")) {
        Ok(lock) => {
            lock.try_lock().map_err(io::Error::from)?;
            Some(lock)
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => None,
        Err(error) => return Err(error),
    };
    if process::find(game_dir).is_some() {
        return Err(io::ErrorKind::WouldBlock.into());
    }
    // Older launcher builds did not persist the PID. Their live game still holds latest.log.
    match OpenOptions::new().read(true).share_mode(0).open(game_dir.join("logs/latest.log")) {
        Ok(_) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error),
    }
    Ok(())
}

#[cfg(windows)]
fn request_window_close(pid: u32) -> bool {
    use std::ffi::c_void;
    type Window = *mut c_void;
    const WM_CLOSE: u32 = 0x0010;
    const GW_OWNER: u32 = 4;

    #[link(name = "user32")]
    extern "system" {
        fn EnumWindows(
            callback: unsafe extern "system" fn(Window, isize) -> i32,
            data: isize,
        ) -> i32;
        fn GetWindowThreadProcessId(window: Window, pid: *mut u32) -> u32;
        fn GetWindow(window: Window, command: u32) -> Window;
        fn IsWindowVisible(window: Window) -> i32;
        fn PostMessageW(window: Window, message: u32, wparam: usize, lparam: isize) -> i32;
    }

    struct Search {
        pid: u32,
        posted: bool,
    }

    unsafe extern "system" fn visit(window: Window, data: isize) -> i32 {
        let search = &mut *(data as *mut Search);
        let mut owner_pid = 0;
        GetWindowThreadProcessId(window, &mut owner_pid);
        if owner_pid == search.pid
            && IsWindowVisible(window) != 0
            && GetWindow(window, GW_OWNER).is_null()
        {
            search.posted = PostMessageW(window, WM_CLOSE, 0, 0) != 0;
        }
        i32::from(!search.posted)
    }

    let mut search = Search { pid, posted: false };
    unsafe { EnumWindows(visit, &mut search as *mut Search as isize) };
    search.posted
}

#[tauri::command]
pub async fn stop_minecraft(app: tauri::AppHandle) -> Result<()> {
    #[cfg(not(windows))]
    {
        let _ = app;
        Err("game_platform".into())
    }
    #[cfg(windows)]
    {
        let game_dir = crate::catalog::instance_dir(&app, VERSION)?;
        tauri::async_runtime::spawn_blocking(move || {
            let Some(process) = process::find(&game_dir) else {
                return Ok(());
            };
            let timeout = Duration::from_secs(30);
            let mut deadline = Instant::now() + timeout;
            let mut sent = false;
            loop {
                if !process.alive() {
                    return Ok(());
                }
                if !sent && process.close_window() {
                    sent = true;
                    deadline = Instant::now() + timeout;
                }
                if Instant::now() >= deadline {
                    return Err(if sent {
                        "game_stop_timeout"
                    } else {
                        "game_window"
                    }
                    .into());
                }
                std::thread::sleep(Duration::from_millis(200));
            }
        })
        .await
        .map_err(|_| "game_install".to_owned())?
    }
}

#[tauri::command]
pub async fn launch_minecraft(
    app: tauri::AppHandle,
    game: tauri::State<'_, Game>,
    minecraft: tauri::State<'_, Minecraft>,
    auth: tauri::State<'_, crate::auth::Auth>,
    options: LaunchOptions,
) -> Result<()> {
    if !cfg!(all(windows, target_arch = "x86_64")) {
        return Err("game_platform".into());
    }
    if options.version != VERSION {
        return Err("game_version".into());
    }
    if !(2..=12).contains(&options.memory)
        || !(320..=8192).contains(&options.width)
        || !(240..=8192).contains(&options.height)
        || options.jvm_arguments.len() > 16384
        || (options.fabric_loader != "auto" && !valid_loader(&options.fabric_loader))
    {
        return Err("game_options".into());
    }
    let extra = split_jvm_args(&options.jvm_arguments)?;
    let game = game.inner().clone();
    let minecraft = minecraft.inner().clone();
    let auth = auth.inner().clone();
    let cancelled = Arc::new(AtomicBool::new(false));
    {
        let mut status = game.status.lock().map_err(|_| "game_install")?;
        if !matches!(*status, Status::Idle) {
            return Err("game_busy".into());
        }
        *status = Status::Preparing(cancelled.clone());
    }
    let active = ActiveGame { game: game.clone(), app: app.clone() };
    tauri::async_runtime::spawn_blocking(move || {
        let _active = active;
        let game_dir = crate::catalog::instance_dir(&app, VERSION)?;
        let cache = app
            .path()
            .app_cache_dir()
            .map_err(|_| "game_storage")?
            .join("vanilla");
        fs::create_dir_all(&game_dir).map_err(io_error)?;
        let lock = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .open(game_dir.join(".gleam-launch.lock"))
            .map_err(io_error)?;
        lock.try_lock().map_err(|_| "game_busy")?;
        #[cfg(windows)]
        if process::find(&game_dir).is_some() {
            return Err("game_busy".into());
        }
        let mut activity = read_activity(&game_dir).ok();
        if let Some(sessions) = &mut activity {
            recover_activity(&game_dir, sessions);
        }
        let progress = |stage: &str, percent: usize| {
            let _ = app.emit(
                "minecraft-launch-progress",
                json!({ "stage": stage, "percent": percent }),
            );
        };
        let verification_path = cache.join("verified-files-v1.json");
        let verified = File::open(&verification_path)
            .ok()
            .and_then(|file| serde_json::from_reader(file).ok())
            .unwrap_or_default();
        let installer = Installer {
            client: Client::builder()
                .user_agent("Gleam-Launcher/0.1.0")
                .connect_timeout(Duration::from_secs(8))
                .timeout(Duration::from_secs(90))
                .redirect(reqwest::redirect::Policy::none())
                .build()
                .map_err(|_| "game_download")?,
            cancelled: &cancelled,
            progress: &progress,
            verified: Mutex::new(verified),
            verification_changed: AtomicBool::new(false),
        };
        progress("files", 0);
        let metadata_path = cache.join(format!(
            "launch-{VERSION}-{}-v1.json",
            options.fabric_loader
        ));
        let cached = LaunchMetadata::read(&metadata_path);
        let cached_installation = cached
            .as_ref()
            .map(|metadata| installer.install(&game_dir, &cache, &options, metadata));
        let (installed, refresh_metadata) = match cached_installation {
            Some(Ok(installed)) => (
                installed,
                cached.as_ref().is_some_and(LaunchMetadata::needs_refresh),
            ),
            Some(Err(error))
                if !matches!(
                    error.as_str(),
                    "game_metadata" | "game_fabric" | "game_integrity" | "game_download"
                ) =>
            {
                return Err(error);
            }
            _ => {
                let metadata = installer.resolve_metadata(&cache, &options.fabric_loader)?;
                let installed = installer.install(&game_dir, &cache, &options, &metadata)?;
                metadata.save(&metadata_path);
                (installed, false)
            }
        };
        installer.save_verifications(&verification_path);
        progress("account", 97);
        let session = minecraft.game_session(&app, &options.account)?;
        let mut command = installed.command(&game_dir, &options, &session, extra)?;
        let gleam_auth = auth.game_auth()?;
        let gleam_session = gleam_auth.as_ref().map(|session| session.stage_game_session(&game_dir)).transpose()?;
        let mut settings_sync = gleam_auth.and_then(|session| {
            progress("settings", 98);
            SettingsSync::prepare(session, &game_dir).ok().flatten()
        });
        command.env_remove("GLEAM_LAUNCH_SESSION");
        if let Some(directory) = &gleam_session {
            command.env("GLEAM_LAUNCH_SESSION", directory.path().join("session.bin"));
        }
        let mut child = {
            let mut status = game.status.lock().map_err(|_| "game_install")?;
            installer.check()?;
            set_window_mode(&game_dir, options.fullscreen)?;
            let mut child = command.spawn().map_err(|_| "game_spawn")?;
            #[cfg(windows)]
            if let Err(error) = process::save(&game_dir, &child) {
                let _ = child.kill();
                let _ = child.wait();
                return Err(error);
            }
            *status = Status::Running { settings_sync: settings_sync.is_some() };
            child
        };
        let started_at = timestamp(SystemTime::now());
        let started = Instant::now();
        if let Some(sessions) = &mut activity {
            sessions.push(PlaySession {
                player: session.name,
                version: VERSION.into(),
                started_at,
                ended_at: None,
            });
            let _ = save_activity(&game_dir, sessions);
        }
        progress("running", 100);
        if refresh_metadata {
            let client = installer.client.clone();
            let loader = options.fabric_loader.clone();
            let _refresh = tauri::async_runtime::spawn_blocking(move || {
                let resolver = Installer {
                    client,
                    cancelled: &AtomicBool::new(false),
                    progress: &|_, _| {},
                    verified: Mutex::new(HashMap::new()),
                    verification_changed: AtomicBool::new(false),
                };
                if let Ok(metadata) = resolver.resolve_metadata(&cache, &loader) {
                    if LaunchMetadata::read(&metadata_path)
                        .is_none_or(|current| current.resolved_at <= metadata.resolved_at)
                    {
                        metadata.save(&metadata_path);
                    }
                }
            });
        }
        let result = if let Some(sync) = &mut settings_sync {
            let interval = Duration::from_secs(5);
            let mut next_save = Instant::now() + interval;
            loop {
                match child.try_wait() {
                    Ok(Some(status)) => {
                        let _ = sync.save(&game_dir, true);
                        break Ok(status);
                    }
                    Ok(None) => {}
                    Err(error) => break Err(error),
                }
                if Instant::now() >= next_save {
                    let _ = sync.save(&game_dir, false);
                    next_save = Instant::now() + interval;
                }
                std::thread::sleep(Duration::from_millis(500));
            }
        } else {
            child.wait()
        };
        if let Some(sessions) = &mut activity {
            if let Some(session) = sessions.last_mut() {
                session.ended_at = Some(started_at.saturating_add(started.elapsed().as_millis() as u64));
            }
            let _ = save_activity(&game_dir, sessions);
        }
        *game.status.lock().map_err(|_| "game_install")? = Status::Finishing;
        let result = result.map_err(|_| "game_exit")?;
        if !result.success() {
            return Err(format!("game_exit:{}", result.code().unwrap_or(-1)));
        }
        Ok(())
    })
    .await
    .map_err(|_| "game_install".to_owned())?
}
