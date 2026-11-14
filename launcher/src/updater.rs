use base64::{engine::general_purpose::STANDARD, Engine};
use p256::ecdsa::{signature::Verifier, Signature, VerifyingKey};
use reqwest::blocking::{Client, Response};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    fs::{self, OpenOptions},
    io::{Read, Write},
    path::Path,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};
use tauri::Emitter;

const RELEASES: &str = "https://gleamclient.xyz/downloads/launcher";
const PUBLIC_KEY: &str = include_str!("../update-public-key.txt");
const MAX_MANIFEST: u64 = 16_384;
const MAX_BINARY: u64 = 128 * 1024 * 1024;
const UPDATE_LOCK: &str = ".gleam-update.lock";
type Result<T> = std::result::Result<T, &'static str>;

#[derive(Clone)]
pub struct Updater {
    active: Arc<Mutex<Option<Arc<AtomicBool>>>>,
    #[cfg(windows)]
    executable: Option<std::path::PathBuf>,
}

impl Default for Updater {
    fn default() -> Self {
        Self {
            active: Arc::new(Mutex::new(None)),
            #[cfg(windows)]
            executable: std::env::current_exe().ok(),
        }
    }
}

struct ActiveUpdate(Updater);

impl Drop for ActiveUpdate {
    fn drop(&mut self) {
        if let Ok(mut active) = self.0.active.lock() {
            *active = None;
        }
    }
}

#[derive(Deserialize)]
struct Envelope {
    payload: String,
    signature: String,
}

#[derive(Deserialize)]
struct Manifest {
    version: String,
    size: u64,
    sha256: String,
}

#[derive(Serialize)]
#[serde(tag = "status", rename_all = "snake_case")]
pub enum UpdateResult {
    Current,
    Unavailable,
    Available { version: String },
    Installed { version: String },
}

fn version(value: &str) -> Option<[u16; 3]> {
    let mut parts = value.split('.');
    let major: u16 = parts.next()?.parse().ok()?;
    let minor: u16 = parts.next()?.parse().ok()?;
    let patch: u16 = parts.next()?.parse().ok()?;
    (value == format!("{major}.{minor}.{patch}")).then_some([major, minor, patch])
}

fn verify_manifest(bytes: &[u8], key: &VerifyingKey) -> Result<Manifest> {
    let envelope: Envelope = serde_json::from_slice(bytes).map_err(|_| "verification")?;
    let payload = STANDARD
        .decode(envelope.payload)
        .map_err(|_| "verification")?;
    let signature = STANDARD
        .decode(envelope.signature)
        .map_err(|_| "verification")?;
    let signature = Signature::from_der(&signature).map_err(|_| "verification")?;
    key.verify(&payload, &signature)
        .map_err(|_| "verification")?;
    let manifest: Manifest = serde_json::from_slice(&payload).map_err(|_| "verification")?;
    if version(&manifest.version).is_none()
        || !(1..=MAX_BINARY).contains(&manifest.size)
        || manifest.sha256.len() != 64
        || !manifest
            .sha256
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
    {
        return Err("verification");
    }
    Ok(manifest)
}

fn check_cancelled(cancelled: &AtomicBool) -> Result<()> {
    if cancelled.load(Ordering::Relaxed) {
        return Err("cancelled");
    }
    Ok(())
}

fn download(
    mut response: impl Read,
    output: &mut impl Write,
    manifest: &Manifest,
    cancelled: &AtomicBool,
    progress: impl Fn(u8),
) -> Result<()> {
    let mut hash = Sha256::new();
    let mut bytes = 0;
    let mut previous = 0;
    let mut buffer = [0; 64 * 1024];
    loop {
        check_cancelled(cancelled)?;
        let count = response.read(&mut buffer).map_err(|_| "network")?;
        if count == 0 {
            break;
        }
        bytes += count as u64;
        if bytes > manifest.size {
            return Err("verification");
        }
        hash.update(&buffer[..count]);
        output.write_all(&buffer[..count]).map_err(|_| "storage")?;
        let percent = (bytes * 100 / manifest.size) as u8;
        if percent != previous {
            progress(percent);
            previous = percent;
        }
    }
    if bytes != manifest.size || format!("{:x}", hash.finalize()) != manifest.sha256 {
        return Err("verification");
    }
    Ok(())
}

fn asset(client: &Client, path: &str, timeout: Duration) -> Result<Response> {
    client
        .get(format!("{RELEASES}/{path}"))
        .header("Cache-Control", "no-cache")
        .timeout(timeout)
        .send()
        .map_err(|_| "network")
}

#[cfg(windows)]
fn verify_staged_version(
    staged: tempfile::NamedTempFile,
    expected: [u16; 3],
) -> Result<tempfile::TempPath> {
    staged.as_file().sync_all().map_err(|_| "storage")?;
    // Win32 cannot read version resources while the staged file is open for writing.
    let staged = staged.into_temp_path();
    if windows::file_version(&staged).map_err(|_| "verification")? != expected {
        return Err("verification");
    }
    Ok(staged)
}

#[cfg(windows)]
fn check(
    app: &tauri::AppHandle,
    updater: &Updater,
    cancelled: &AtomicBool,
    requested: Option<&str>,
) -> Result<UpdateResult> {
    let current = version(env!("CARGO_PKG_VERSION")).ok_or("verification")?;
    let executable = updater.executable.as_deref().ok_or("storage")?;
    let directory = executable.parent().ok_or("storage")?;
    let disk_version = windows::installed_version(executable)?;
    if disk_version > current {
        let [major, minor, patch] = disk_version;
        return Ok(UpdateResult::Installed {
            version: format!("{major}.{minor}.{patch}"),
        });
    }
    check_cancelled(cancelled)?;
    let client = Client::builder()
        .https_only(true)
        .redirect(reqwest::redirect::Policy::none())
        .user_agent(concat!("Gleam-Launcher/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(8))
        .build()
        .map_err(|_| "network")?;
    let response = asset(&client, "launcher-update.json", Duration::from_secs(5))?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(UpdateResult::Unavailable);
    }
    let response = response.error_for_status().map_err(|_| "network")?;
    let mut bytes = Vec::new();
    response
        .take(MAX_MANIFEST + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| "network")?;
    if bytes.len() as u64 > MAX_MANIFEST {
        return Err("verification");
    }
    let key = STANDARD
        .decode(PUBLIC_KEY.trim())
        .map_err(|_| "verification")?;
    let key = VerifyingKey::from_sec1_bytes(&key).map_err(|_| "verification")?;
    let manifest = verify_manifest(&bytes, &key)?;
    let next = version(&manifest.version).ok_or("verification")?;
    if next <= current {
        return Ok(UpdateResult::Current);
    }
    if requested != Some(manifest.version.as_str()) {
        return Ok(UpdateResult::Available { version: manifest.version });
    }
    check_cancelled(cancelled)?;
    let response = asset(&client, &format!("{}/Gleam.Launcher.exe", manifest.version), Duration::from_secs(120))?
        .error_for_status()
        .map_err(|_| "network")?;
    if response
        .content_length()
        .is_some_and(|length| length != manifest.size)
    {
        return Err("verification");
    }
    let mut staged = tempfile::Builder::new()
        .suffix(".exe")
        .tempfile_in(directory)
        .map_err(|_| "storage")?;
    download(response, &mut staged, &manifest, cancelled, |percent| {
        let _ = app.emit("launcher-update-progress", percent);
    })?;
    let staged = verify_staged_version(staged, next)?;
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(directory.join(UPDATE_LOCK))
        .map_err(|_| "storage")?;
    lock.try_lock().map_err(|_| "busy")?;
    let _active = updater.active.lock().map_err(|_| "busy")?;
    check_cancelled(cancelled)?;
    let disk_version = windows::installed_version(executable)?;
    if disk_version >= next {
        let [major, minor, patch] = disk_version;
        return Ok(UpdateResult::Installed {
            version: format!("{major}.{minor}.{patch}"),
        });
    }
    windows::replace(executable, &staged)?;
    Ok(UpdateResult::Installed {
        version: manifest.version,
    })
}

#[tauri::command]
pub async fn check_launcher_update(
    app: tauri::AppHandle,
    updater: tauri::State<'_, Updater>,
) -> Result<UpdateResult> {
    run_update(app, updater.inner().clone(), None).await
}

#[tauri::command]
pub async fn install_launcher_update(
    app: tauri::AppHandle,
    updater: tauri::State<'_, Updater>,
    requested: String,
) -> Result<UpdateResult> {
    if version(&requested).is_none() {
        return Err("verification");
    }
    run_update(app, updater.inner().clone(), Some(requested)).await
}

async fn run_update(
    app: tauri::AppHandle,
    updater: Updater,
    requested: Option<String>,
) -> Result<UpdateResult> {
    #[cfg(not(windows))]
    {
        let _ = (app, updater, requested);
        Ok(UpdateResult::Unavailable)
    }
    #[cfg(windows)]
    {
        let cancelled = Arc::new(AtomicBool::new(false));
        {
            let mut active = updater.active.lock().map_err(|_| "busy")?;
            if active.is_some() {
                return Err("busy");
            }
            *active = Some(cancelled.clone());
        }
        let guard = ActiveUpdate(updater.clone());
        tauri::async_runtime::spawn_blocking(move || {
            let _guard = guard;
            check(&app, &updater, &cancelled, requested.as_deref())
        })
        .await
        .map_err(|_| "storage")?
    }
}

#[tauri::command]
pub async fn restart_launcher_update(
    app: tauri::AppHandle,
    updater: tauri::State<'_, Updater>,
    game: tauri::State<'_, crate::game::Game>,
) -> Result<()> {
    #[cfg(not(windows))]
    {
        let _ = (app, updater, game);
        Err("unavailable")
    }
    #[cfg(windows)]
    {
        let active = updater.active.lock().map_err(|_| "busy")?;
        if active.is_some() {
            return Err("busy");
        }
        let executable = updater.executable.as_deref().ok_or("storage")?;
        let current = version(env!("CARGO_PKG_VERSION")).ok_or("verification")?;
        if windows::installed_version(executable)? <= current {
            return Err("verification");
        }
        game.restart_when_idle(|| {
            if crate::game::minecraft_running(app.clone()).map_err(|_| "game_busy")?.is_some() {
                return Err("game_busy");
            }
            std::process::Command::new(executable)
                .args(std::env::args_os().skip(1))
                .spawn()
                .map_err(|_| "restart")?;
            Ok(())
        })?;
        app.exit(0);
        Ok(())
    }
}

#[tauri::command]
pub fn cancel_launcher_update(updater: tauri::State<'_, Updater>) {
    if let Ok(active) = updater.active.lock() {
        if let Some(cancelled) = &*active {
            cancelled.store(true, Ordering::Relaxed);
        }
    }
}

#[cfg(windows)]
mod windows {
    use super::*;
    use std::{ffi::c_void, os::windows::ffi::OsStrExt, ptr};

    #[link(name = "version")]
    extern "system" {
        fn GetFileVersionInfoSizeW(path: *const u16, handle: *mut u32) -> u32;
        fn GetFileVersionInfoW(path: *const u16, handle: u32, size: u32, data: *mut c_void) -> i32;
        fn VerQueryValueW(
            data: *const c_void,
            sub_block: *const u16,
            value: *mut *mut c_void,
            len: *mut u32,
        ) -> i32;
    }

    #[link(name = "kernel32")]
    extern "system" {
        fn ReplaceFileW(
            target: *const u16,
            staged: *const u16,
            backup: *const u16,
            flags: u32,
            exclude: *mut c_void,
            reserved: *mut c_void,
        ) -> i32;
    }

    fn wide(path: &Path) -> Vec<u16> {
        path.as_os_str().encode_wide().chain([0]).collect()
    }

    pub(super) fn installed_version(executable: &Path) -> Result<[u16; 3]> {
        // Querying the loaded executable's original path can return its old
        // version resource after ReplaceFileW. Read a snapshot under a fresh name.
        let mut snapshot = tempfile::Builder::new()
            .suffix(".exe")
            .tempfile()
            .map_err(|_| "storage")?;
        let mut file = fs::File::open(executable).map_err(|_| "storage")?;
        std::io::copy(&mut file, &mut snapshot).map_err(|_| "storage")?;
        let snapshot = snapshot.into_temp_path();
        file_version(&snapshot)
    }

    pub(super) fn file_version(path: &Path) -> Result<[u16; 3]> {
        let path = wide(path);
        let mut handle = 0;
        let size = unsafe { GetFileVersionInfoSizeW(path.as_ptr(), &mut handle) };
        if size == 0 || size > 1024 * 1024 {
            return Err("storage");
        }
        let mut data = vec![0u32; (size as usize).div_ceil(4)];
        if unsafe { GetFileVersionInfoW(path.as_ptr(), 0, size, data.as_mut_ptr().cast()) } == 0 {
            return Err("storage");
        }
        let root = [b'\\' as u16, 0];
        let mut value = ptr::null_mut();
        let mut length = 0;
        if unsafe { VerQueryValueW(data.as_ptr().cast(), root.as_ptr(), &mut value, &mut length) }
            == 0
            || value.is_null()
            || length < 52
        {
            return Err("storage");
        }
        let info = unsafe { value.cast::<[u32; 4]>().read_unaligned() };
        if info[0] != 0xFEEF04BD {
            return Err("storage");
        }
        Ok([
            (info[2] >> 16) as u16,
            info[2] as u16,
            (info[3] >> 16) as u16,
        ])
    }

    pub(super) fn replace(executable: &Path, staged: &Path) -> Result<()> {
        let backup = executable.with_file_name(".gleam-launcher.previous.exe");
        let replaced = unsafe {
            ReplaceFileW(
                wide(executable).as_ptr(),
                wide(staged).as_ptr(),
                wide(&backup).as_ptr(),
                0,
                ptr::null_mut(),
                ptr::null_mut(),
            )
        };
        if replaced != 0 {
            return Ok(());
        }
        if !executable.exists() && backup.is_file() {
            let _ = fs::rename(backup, executable);
        }
        Err("storage")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::{signature::Signer, SigningKey};
    use serde_json::json;

    #[test]
    fn accepts_only_signed_manifests_and_matching_binaries() {
        let signing = SigningKey::from_bytes((&[7u8; 32]).into()).unwrap();
        let file = b"test launcher";
        let payload = serde_json::to_vec(&json!({
            "version": "0.1.2", "size": file.len(), "sha256": format!("{:x}", Sha256::digest(file))
        }))
        .unwrap();
        let signature: Signature = signing.sign(&payload);
        let envelope = |payload: &[u8]| {
            serde_json::to_vec(&json!({
                "payload": STANDARD.encode(payload),
                "signature": STANDARD.encode(signature.to_der().as_bytes())
            }))
            .unwrap()
        };
        let manifest = verify_manifest(&envelope(&payload), signing.verifying_key()).unwrap();
        assert!(verify_manifest(&envelope(b"modified"), signing.verifying_key()).is_err());
        assert!(version("0.01.2").is_none());
        assert!(version("0.1.2-beta").is_none());
        let cancelled = AtomicBool::new(false);
        let mut output = Vec::new();
        download(file.as_slice(), &mut output, &manifest, &cancelled, |_| {}).unwrap();
        assert_eq!(output, file);
        assert_eq!(
            download(
                b"wrong binary!".as_slice(),
                &mut output,
                &manifest,
                &cancelled,
                |_| {}
            ),
            Err("verification")
        );
        cancelled.store(true, Ordering::Relaxed);
        assert_eq!(
            download(file.as_slice(), &mut output, &manifest, &cancelled, |_| {}),
            Err("cancelled")
        );
    }

    #[cfg(windows)]
    #[test]
    fn updates_a_running_windows_executable_with_a_backup() {
        use std::{
            os::windows::process::CommandExt,
            process::{Command, Stdio},
        };
        let directory = tempfile::tempdir().unwrap();
        let executable = directory.path().join("running.exe");
        let shell = std::env::var_os("ComSpec").unwrap();
        fs::copy(shell, &executable).unwrap();
        let test_binary = std::env::current_exe().unwrap();
        let updated_version = windows::file_version(&test_binary).unwrap();
        let mut staged = tempfile::Builder::new()
            .suffix(".exe")
            .tempfile_in(directory.path())
            .unwrap();
        std::io::copy(&mut fs::File::open(test_binary).unwrap(), &mut staged).unwrap();
        let staged = verify_staged_version(staged, updated_version).unwrap();
        let original_version = windows::file_version(&executable).unwrap();
        let mut child = Command::new(&executable)
            .args(["/d", "/c", "pause"])
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .creation_flags(0x0800_0000) // CREATE_NO_WINDOW
            .spawn()
            .unwrap();
        let result = windows::replace(&executable, &staged);
        let still_running = child.try_wait().unwrap().is_none();
        let _ = child.kill();
        let _ = child.wait();
        assert!(still_running);
        result.unwrap();
        assert_eq!(windows::file_version(&executable).unwrap(), updated_version);
        assert_eq!(windows::installed_version(&executable).unwrap(), updated_version);
        let backup = directory.path().join(".gleam-launcher.previous.exe");
        assert_eq!(windows::file_version(&backup).unwrap(), original_version);
    }
}
