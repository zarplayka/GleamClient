use reqwest::{blocking::{Client, RequestBuilder, Response}, Method};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    fs,
    io::Read,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};
use tauri::Manager;

const ORIGIN: &str = "https://gleamclient.xyz";
const COOKIE: &str = "__Host-orion_session";

#[derive(Clone)]
pub struct Auth(Arc<Mutex<Inner>>, Arc<AtomicU64>);

struct Inner {
    client: Client,
    session: Option<Session>,
    pending: Option<Pending>,
}

#[derive(Serialize, Deserialize)]
struct Session {
    token: String,
    csrf: String,
    #[serde(default)]
    account_id: Option<u64>,
}

pub(crate) struct GameAuth {
    pub(crate) account_id: Option<u64>,
    token: String,
    csrf: String,
    client: Client,
}
struct Pending {
    secret: String,
    url: String,
}

impl Auth {
    pub fn new() -> Self {
        Self(
            Arc::new(Mutex::new(Inner {
                client: Client::builder()
                    .user_agent("Gleam-Launcher/0.1.0")
                    .redirect(reqwest::redirect::Policy::none())
                    .connect_timeout(Duration::from_secs(5))
                    .timeout(Duration::from_secs(15))
                    .build()
                    .expect("launcher HTTP client"),
                session: None,
                pending: None,
            })),
            Arc::new(AtomicU64::new(0)),
        )
    }

    pub(crate) fn game_auth(&self) -> Result<Option<GameAuth>, String> {
        let inner = self.0.lock().map_err(|_| "game_storage")?;
        Ok(inner.session.as_ref().map(|session| GameAuth {
            account_id: session.account_id,
            token: session.token.clone(),
            csrf: session.csrf.clone(),
            client: inner.client.clone(),
        }))
    }
}

impl GameAuth {
    fn account_request(&self, method: Method, path: &str) -> RequestBuilder {
        self.client.request(method, format!("{ORIGIN}{path}"))
            .header("Origin", ORIGIN)
            .header("X-Orion-Request", "1")
            .header("X-CSRF-Token", &self.csrf)
            .header("Cookie", format!("{COOKIE}={}", self.token))
            .timeout(Duration::from_secs(3))
    }

    pub(crate) fn settings_request(&self, method: Method) -> RequestBuilder {
        self.account_request(method, "/api/game-settings")
    }

    pub(crate) fn stage_game_session(&self, game_dir: &Path) -> Result<tempfile::TempDir, String> {
        let directory = tempfile::Builder::new()
            .prefix(".gleam-login-")
            .tempdir_in(game_dir)
            .map_err(|_| "game_storage")?;
        write_protected(&directory.path().join("session.bin"), self.token.as_bytes())
            .map_err(|_| "game_storage")?;
        Ok(directory)
    }
}

#[tauri::command]
pub async fn cosmetic_loadout(
    auth: tauri::State<'_, Auth>,
    slot: Option<String>,
    value: Option<Value>,
) -> Result<Value, String> {
    let state = auth.inner().clone();
    let generation = state.1.load(Ordering::SeqCst);
    tauri::async_runtime::spawn_blocking(move || {
        let session = state.game_auth()?.ok_or("auth")?;
        let path = "/api/cosmetic-loadout";
        let request = match (slot, value) {
            (None, None) => session.account_request(Method::GET, path),
            (Some(slot), Some(value)) => session.account_request(Method::POST, path)
                .json(&json!({"changes": { (slot): value }})),
            _ => return Err("server".into()),
        };
        let result = response(request.send().map_err(|_| "network")?)?;
        if generation != state.1.load(Ordering::SeqCst) { return Err("auth".into()); }
        Ok(result)
    }).await.map_err(|_| "server")?
}

fn valid_secret(value: &str) -> bool {
    value.len() == 43
        && value
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
}

#[tauri::command]
pub async fn gleam_friends(
    auth: tauri::State<'_, Auth>,
    action: String,
    target: Option<String>,
    autojoin: Option<bool>,
) -> Result<Value, String> {
    let state = auth.inner().clone();
    let generation = state.1.load(Ordering::SeqCst);
    tauri::async_runtime::spawn_blocking(move || {
        let session = state.game_auth()?.ok_or("auth")?;
        let target = target.unwrap_or_default();
        let valid = target.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_');
        let invite_id = target.len() == 32
            && target.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-');
        let request = match action.as_str() {
            "list" => session.account_request(Method::GET, "/api/friends"),
            "presence" => session.account_request(Method::POST, "/api/friend-worlds")
                .json(&json!({"action": "presence", "device": "launcher"})),
            "worlds" => session.account_request(Method::GET, "/api/friend-worlds"),
            "profile" if target.len() == 16 && target.bytes().all(|c| c.is_ascii_hexdigit()) =>
                session.account_request(Method::GET, &format!("/api/profile/{target}")),
            "world-invite" if valid && (3..=24).contains(&target.len()) =>
                session.account_request(Method::POST, "/api/friend-worlds")
                    .json(&json!({"action": "invite", "username": target})),
            "world-reserve" | "world-decline" | "world-cancel" if invite_id =>
                session.account_request(Method::POST, "/api/friend-worlds")
                    .json(&json!({"action": &action[6..], "id": target, "autojoin": autojoin.unwrap_or(false)})),
            "request" | "accept" | "decline" | "cancel" | "remove" if valid && (3..=24).contains(&target.len()) =>
                session.account_request(Method::POST, "/api/friends")
                    .json(&json!({"action": action, "username": target})),
            _ => return Err("invalid".into()),
        };
        let result = response(request.send().map_err(|_| "network")?)?;
        if generation != state.1.load(Ordering::SeqCst) {
            return Err("auth".into());
        }
        Ok(result)
    }).await.map_err(|_| "server")?
}

fn response(response: Response) -> Result<Value, String> {
    let status = response.status();
    let mut bytes = Vec::new();
    response
        .take(65537)
        .read_to_end(&mut bytes)
        .map_err(|_| "network")?;
    if bytes.len() > 65536 {
        return Err("server".into());
    }
    let data: Value = serde_json::from_slice(&bytes).map_err(|_| "server")?;
    if !status.is_success() {
        let code = data
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or("server");
        return Err(match code {
            "credentials" | "banned" | "rate" | "auth" | "launcher_expired" => code,
            "friend_missing" | "friend_self" | "friend_state" | "friend_limit" | "friend_unavailable"
            | "world_expired" | "world_unavailable" | "world_license" => code,
            _ => "server",
        }
        .into());
    }
    Ok(data)
}

fn post(
    inner: &Inner,
    path: &str,
    body: Value,
) -> Result<reqwest::blocking::RequestBuilder, String> {
    let mut request = inner
        .client
        .post(format!("{ORIGIN}{path}"))
        .header("Origin", ORIGIN)
        .header("X-Orion-Request", "1")
        .json(&body);
    if let Some(session) = &inner.session {
        request = request
            .header("Cookie", format!("{COOKIE}={}", session.token))
            .header("X-CSRF-Token", &session.csrf);
    }
    Ok(request)
}

fn path(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_data_dir()
        .map(|p| p.join("gleam-session.dat"))
        .map_err(|_| "storage".into())
}

#[cfg(target_os = "windows")]
pub(crate) fn protect(bytes: &[u8], decrypt: bool) -> Result<Vec<u8>, String> {
    use std::ffi::c_void;
    #[repr(C)]
    struct Blob {
        len: u32,
        data: *mut u8,
    }
    #[link(name = "crypt32")]
    extern "system" {
        fn CryptProtectData(
            input: *const Blob,
            description: *const u16,
            entropy: *const Blob,
            reserved: *mut c_void,
            prompt: *mut c_void,
            flags: u32,
            output: *mut Blob,
        ) -> i32;
        fn CryptUnprotectData(
            input: *const Blob,
            description: *mut *mut u16,
            entropy: *const Blob,
            reserved: *mut c_void,
            prompt: *mut c_void,
            flags: u32,
            output: *mut Blob,
        ) -> i32;
    }
    #[link(name = "kernel32")]
    extern "system" {
        fn LocalFree(memory: *mut c_void) -> *mut c_void;
    }
    let input = Blob {
        len: bytes.len().try_into().map_err(|_| "storage")?,
        data: bytes.as_ptr() as *mut u8,
    };
    let mut output = Blob {
        len: 0,
        data: std::ptr::null_mut(),
    };
    unsafe {
        let ok = if decrypt {
            CryptUnprotectData(
                &input,
                std::ptr::null_mut(),
                std::ptr::null(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                1,
                &mut output,
            )
        } else {
            CryptProtectData(
                &input,
                std::ptr::null(),
                std::ptr::null(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                1,
                &mut output,
            )
        };
        if ok == 0 {
            return Err("storage".into());
        }
        let result = std::slice::from_raw_parts(output.data, output.len as usize).to_vec();
        LocalFree(output.data.cast());
        Ok(result)
    }
}

#[cfg(not(target_os = "windows"))]
pub(crate) fn protect(_: &[u8], _: bool) -> Result<Vec<u8>, String> {
    Err("secure_storage_unavailable".into())
}

fn persist(app: &tauri::AppHandle, session: &Session) -> Result<(), String> {
    write_protected(
        &path(app)?,
        &serde_json::to_vec(session).map_err(|_| "storage")?,
    )
}

pub(crate) fn write_protected(target: &std::path::Path, data: &[u8]) -> Result<(), String> {
    use std::io::Write;
    let parent = target.parent().ok_or("storage")?;
    fs::create_dir_all(parent).map_err(|_| "storage")?;
    let bytes = protect(data, false)?;
    let mut staged = tempfile::NamedTempFile::new_in(parent).map_err(|_| "storage")?;
    staged.write_all(&bytes).map_err(|_| "storage")?;
    staged.as_file().sync_all().map_err(|_| "storage")?;
    staged.persist(target).map_err(|_| "storage")?;
    Ok(())
}

fn profile(inner: &mut Inner) -> Result<Value, String> {
    let session = inner.session.as_ref().ok_or("auth")?;
    let data = response(
        inner
            .client
            .get(format!("{ORIGIN}/api/me"))
            .header("Cookie", format!("{COOKIE}={}", session.token))
            .send()
            .map_err(|_| "network")?,
    )?;
    let user = data
        .get("user")
        .filter(|user| user.get("username").and_then(Value::as_str).is_some())
        .ok_or("auth")?;
    if let Some(session) = &mut inner.session {
        session.account_id = data["account_id"].as_u64().filter(|id| *id > 0).or(session.account_id);
    }
    // Credentials stay in Rust; the renderer only receives public account data.
    Ok(
        json!({"user": {"username": user["username"], "role": user["role"]},
        "plus_active": data["plus_active"], "plus_expires_at": data["plus_expires_at"],
        "avatar_url": data["avatar_url"], "launcher_version": env!("CARGO_PKG_VERSION")}),
    )
}

#[tauri::command]
pub async fn gleam_auth(
    app: tauri::AppHandle,
    auth: tauri::State<'_, Auth>,
    action: String,
    username: Option<String>,
    password: Option<String>,
) -> Result<Value, String> {
    let state = auth.inner().clone();
    if matches!(action.as_str(), "cancel" | "login" | "browser" | "logout") {
        state.1.fetch_add(1, Ordering::SeqCst);
    }
    let generation = state.1.load(Ordering::SeqCst);
    tauri::async_runtime::spawn_blocking(move || {
        let mut inner = state.0.lock().map_err(|_| "server")?;
        match action.as_str() {
            "profile_id" => {
                let session = inner.session.as_ref().ok_or("auth")?;
                let request = inner
                    .client
                    .get(format!("{ORIGIN}/api/profile"))
                    .header("Cookie", format!("{COOKIE}={}", session.token));
                drop(inner);
                let data = response(request.send().map_err(|_| "network")?)?;
                Ok(json!({"id":data["id"], "created_at":data["created_at"]}))
            }
            "restore" => {
                let target = path(&app)?;
                if !target.exists() {
                    return Ok(json!({"user":null}));
                }
                let load = || -> Result<Session, String> {
                    let bytes = fs::read(&target).map_err(|_| "storage")?;
                    if bytes.len() > 16384 {
                        return Err("storage".into());
                    }
                    let session: Session =
                        serde_json::from_slice(&protect(&bytes, true)?).map_err(|_| "storage")?;
                    if !valid_secret(&session.token) || !valid_secret(&session.csrf) {
                        return Err("storage".into());
                    }
                    Ok(session)
                };
                inner.session = match load() {
                    Ok(session) => Some(session),
                    Err(_) => {
                        fs::remove_file(target).map_err(|_| "storage")?;
                        return Ok(json!({"user":null}));
                    }
                };
                let previous_id = inner.session.as_ref().and_then(|session| session.account_id);
                match profile(&mut inner) {
                    Err(error) if error == "auth" => {
                        inner.session = None;
                        fs::remove_file(target).map_err(|_| "storage")?;
                        Ok(json!({"user":null}))
                    }
                    Ok(data) => {
                        if let Some(session) = inner.session.as_ref().filter(|session| session.account_id != previous_id) {
                            persist(&app, session)?;
                        }
                        Ok(data)
                    }
                    Err(error) => Err(error),
                }
            }
            "login" => {
                let name = username.unwrap_or_default();
                let pass = password.unwrap_or_default();
                if name.is_empty() || name.len() > 24 || pass.is_empty() || pass.len() > 512 {
                    return Err("credentials".into());
                }
                let reply = post(
                    &inner,
                    "/api/login",
                    json!({"username":name,"password":pass}),
                )?
                .send()
                .map_err(|_| "network")?;
                let token = reply
                    .headers()
                    .get_all("set-cookie")
                    .iter()
                    .filter_map(|header| header.to_str().ok())
                    .find_map(|header| {
                        header
                            .split(';')
                            .next()?
                            .strip_prefix(&format!("{COOKIE}="))
                    })
                    .map(str::to_string);
                let data = response(reply)?;
                let token = token.ok_or("server")?;
                let csrf = data["csrf"].as_str().ok_or("server")?.to_string();
                if !valid_secret(&token) || !valid_secret(&csrf) {
                    return Err("server".into());
                }
                inner.session = Some(Session { token, csrf, account_id: None });
                inner.pending = None;
                let data = profile(&mut inner)?;
                persist(&app, inner.session.as_ref().ok_or("auth")?)?;
                Ok(data)
            }
            "browser" => {
                let data = response(
                    post(&inner, "/api/launcher-auth/start", json!({}))?
                        .send()
                        .map_err(|_| "network")?,
                )?;
                let secret = data["secret"]
                    .as_str()
                    .filter(|s| valid_secret(s))
                    .ok_or("server")?
                    .to_string();
                let code = data["code"]
                    .as_str()
                    .filter(|s| s.len() == 8 && s.bytes().all(|c| c.is_ascii_hexdigit()))
                    .ok_or("server")?;
                let url = format!("{ORIGIN}/auth.html?launcher={code}");
                inner.pending = Some(Pending {
                    secret,
                    url: url.clone(),
                });
                super::open_browser(&url)?;
                Ok(json!({"code":code,"expires_in":300}))
            }
            "reopen" => {
                super::open_browser(&inner.pending.as_ref().ok_or("launcher_expired")?.url)?;
                Ok(json!({"ok":true}))
            }
            "cancel" => {
                inner.pending = None;
                Ok(json!({"ok":true}))
            }
            "poll" => {
                let secret = &inner.pending.as_ref().ok_or("launcher_expired")?.secret;
                let data = response(
                    post(&inner, "/api/launcher-auth/poll", json!({"secret":secret}))?
                        .send()
                        .map_err(|_| "network")?,
                )?;
                if generation != state.1.load(Ordering::SeqCst) {
                    return Err("launcher_expired".into());
                }
                if data["status"] == "pending" {
                    return Ok(json!({"status":"pending"}));
                }
                let token = data["token"]
                    .as_str()
                    .filter(|s| valid_secret(s))
                    .ok_or("server")?
                    .to_string();
                let csrf = data["csrf"]
                    .as_str()
                    .filter(|s| valid_secret(s))
                    .ok_or("server")?
                    .to_string();
                inner.session = Some(Session { token, csrf, account_id: None });
                inner.pending = None;
                let data = profile(&mut inner)?;
                persist(&app, inner.session.as_ref().ok_or("auth")?)?;
                Ok(data)
            }
            "logout" => {
                if inner.session.is_some() {
                    response(
                        post(&inner, "/api/logout", json!({}))?
                            .send()
                            .map_err(|_| "network")?,
                    )?;
                }
                let target = path(&app)?;
                if target.exists() {
                    fs::remove_file(target).map_err(|_| "storage")?;
                }
                inner.session = None;
                inner.pending = None;
                Ok(json!({"user":null}))
            }
            _ => Err("server".into()),
        }
    })
    .await
    .map_err(|_| "server".to_string())?
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn validates_session_material() {
        assert!(valid_secret(&"a".repeat(43)));
        assert!(!valid_secret(&"a".repeat(42)));
        assert!(!valid_secret(&format!("{};", "a".repeat(42))));
    }
    #[cfg(target_os = "windows")]
    #[test]
    fn windows_protection_roundtrip() {
        let bytes = br#"{"token":"test-session-material","csrf":"test-csrf"}"#;
        let encrypted = protect(bytes, false).unwrap();
        assert!(!encrypted
            .windows(21)
            .any(|part| part == b"test-session-material"));
        assert_eq!(protect(&encrypted, true).unwrap(), bytes);
        assert!(protect(b"invalid storage", true).is_err());
    }

    #[cfg(windows)]
    #[test]
    fn stages_only_an_active_session_and_cleans_it_up() {
        let auth = Auth::new();
        let profile = tempfile::tempdir().unwrap();
        assert!(auth.game_auth().unwrap().is_none());
        let token = "a".repeat(43);
        auth.0.lock().unwrap().session = Some(Session {
            token: token.clone(),
            csrf: "b".repeat(43),
            account_id: None,
        });
        let handoff = auth.game_auth().unwrap().unwrap().stage_game_session(profile.path()).unwrap();
        let path = handoff.path().join("session.bin");
        let encrypted = fs::read(&path).unwrap();
        assert_eq!(protect(&encrypted, true).unwrap(), token.as_bytes());
        drop(handoff);
        assert!(!path.exists());
    }
}
