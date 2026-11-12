//! Microsoft device login and Xbox SISU exchange, following AWLauncher's title_auth flow.
use base64::{
    engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD},
    Engine,
};
use p256::ecdsa::{signature::Signer, Signature, SigningKey};
use reqwest::{
    blocking::{Client, Response},
    Url,
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    fs,
    io::Read,
    sync::{Arc, Mutex},
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tauri::{Emitter, Manager};

const TITLE: &str = "00000000402b5328";
const SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";
const TOKEN_URL: &str = "https://login.live.com/oauth20_token.srf";
const MINECRAFT: &str = "https://api.minecraftservices.com";
type Result<T> = std::result::Result<T, String>;

#[derive(Clone)]
pub struct Minecraft {
    client: Arc<Result<Client>>,
    login: Arc<Mutex<Login>>,
    poll: Arc<Mutex<()>>,
    storage: Arc<Mutex<()>>,
}

impl Default for Minecraft {
    fn default() -> Self {
        Self {
            client: Arc::new(
                Client::builder()
                    .user_agent("Gleam-Launcher/0.1.0")
                    .connect_timeout(Duration::from_secs(5))
                    .timeout(Duration::from_secs(12))
                    .redirect(reqwest::redirect::Policy::none())
                    .build()
                    .map_err(|_| "mc_network".into()),
            ),
            login: Arc::new(Mutex::new(Login::default())),
            poll: Arc::new(Mutex::new(())),
            storage: Arc::new(Mutex::new(())),
        }
    }
}

#[derive(Default)]
struct Login {
    generation: u64,
    device: Option<Device>,
}

#[derive(Clone)]
struct Device {
    code: String,
    secret: String,
    url: String,
    cookies: String,
    deadline: Instant,
    next_poll: Instant,
    interval: Duration,
    failed_polls: u8,
}

#[derive(Serialize, Deserialize)]
struct Profile {
    id: String,
    name: String,
    skin_url: Option<String>,
    slim: bool,
}

#[derive(Serialize, Deserialize)]
struct Account {
    profile: Profile,
    access_token: String,
    refresh_token: String,
    expires_at: u64,
}

#[derive(Default, Serialize, Deserialize)]
struct Accounts {
    selected: Option<String>,
    accounts: Vec<Account>,
}

fn accounts_path(app: &tauri::AppHandle) -> Result<std::path::PathBuf> {
    Ok(app.path().app_data_dir().map_err(|_| "storage")?.join("minecraft-accounts.dat"))
}

fn load_accounts(path: &std::path::Path) -> Result<Accounts> {
    match fs::read(path) {
        Ok(bytes) if bytes.len() <= 524288 => {
            serde_json::from_slice(&crate::auth::protect(&bytes, true)?)
                .map_err(|_| "storage".into())
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(Accounts::default()),
        _ => Err("storage".into()),
    }
}

fn save_accounts(path: &std::path::Path, accounts: &Accounts) -> Result<()> {
    crate::auth::write_protected(path, &serde_json::to_vec(accounts).map_err(|_| "storage")?)
}

pub(crate) struct GameSession {
    pub name: String,
    pub uuid: String,
    pub token: String,
}

impl Minecraft {
    pub(crate) fn game_session(&self, app: &tauri::AppHandle, id: &str) -> Result<GameSession> {
        let _storage = self.storage.lock().map_err(|_| "storage")?;
        let path = accounts_path(app)?;
        let mut accounts = load_accounts(&path)?;
        let index = accounts.accounts.iter().position(|account| account.profile.id == id)
            .ok_or("mc_account_required")?;
        if accounts.accounts[index].expires_at <= unix_time().saturating_add(120) {
            let client = self.client.as_ref().as_ref().map_err(Clone::clone)?;
            let refresh = &accounts.accounts[index].refresh_token;
            let reply = send(client.post(TOKEN_URL).form(&[
                ("client_id", TITLE),
                ("scope", SCOPE),
                ("grant_type", "refresh_token"),
                ("refresh_token", refresh),
            ]), "microsoft_token")?;
            let success = reply.status().is_success();
            let mut data = body(reply, "microsoft_token")?;
            if !success {
                return Err(if matches!(data["error"].as_str(), Some("invalid_grant" | "interaction_required")) {
                    "mc_reauth"
                } else {
                    "mc_service"
                }.into());
            }
            if data["refresh_token"].as_str().is_none_or(str::is_empty) {
                data["refresh_token"] = json!(refresh);
            }
            // Persist a rotated refresh token even if the later Xbox exchange fails.
            accounts.accounts[index].refresh_token = required(&data, "refresh_token")?.to_owned();
            save_accounts(&path, &accounts)?;
            let updated = finish(client, data, |_| Ok(()))?;
            if updated.profile.id != id {
                return Err("mc_reauth".into());
            }
            accounts.accounts[index] = updated;
            save_accounts(&path, &accounts)?;
        }
        let account = &accounts.accounts[index];
        Ok(GameSession {
            name: account.profile.name.clone(),
            uuid: account.profile.id.clone(),
            token: account.access_token.clone(),
        })
    }
}

impl Accounts {
    fn public(&self) -> Value {
        json!({"selected":self.selected,"accounts":self.accounts.iter()
            .map(|account| &account.profile).collect::<Vec<_>>()})
    }
}

fn body(response: Response, stage: &str) -> Result<Value> {
    let mut bytes = Vec::new();
    response
        .take(262145)
        .read_to_end(&mut bytes)
        .map_err(|error| {
            let kind = if error.kind() == std::io::ErrorKind::TimedOut {
                "mc_timeout"
            } else {
                "mc_network"
            };
            format!("{kind}:{stage}")
        })?;
    if bytes.len() > 262144 {
        return Err("mc_service".into());
    }
    serde_json::from_slice(&bytes).map_err(|_| "mc_service".into())
}

fn send(request: reqwest::blocking::RequestBuilder, stage: &str) -> Result<Response> {
    request.send().map_err(|error| {
        let kind = if error.is_timeout() {
            "mc_timeout"
        } else {
            "mc_network"
        };
        format!("{kind}:{stage}")
    })
}

fn checked(response: Response, stage: &str) -> Result<Value> {
    if !response.status().is_success() {
        return Err("mc_service".into());
    }
    body(response, stage)
}

fn required<'a>(data: &'a Value, field: &str) -> Result<&'a str> {
    data[field]
        .as_str()
        .filter(|s| !s.is_empty())
        .ok_or_else(|| "mc_service".into())
}

fn begin(client: &Client) -> Result<Device> {
    let response = send(
        client
            .post("https://login.live.com/oauth20_connect.srf")
            .form(&[
                ("client_id", TITLE),
                ("scope", SCOPE),
                ("response_type", "device_code"),
            ]),
        "microsoft_code",
    )?;
    let cookies = response
        .headers()
        .get_all(reqwest::header::SET_COOKIE)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .filter_map(|s| s.split(';').next())
        .collect::<Vec<_>>()
        .join("; ");
    let data = checked(response, "microsoft_code")?;
    let url = data["verification_uri"]
        .as_str()
        .or(data["verification_url"].as_str())
        .and_then(|url| Url::parse(url).ok())
        .filter(|url| {
            url.scheme() == "https"
                && url.username().is_empty()
                && url.password().is_none()
                && matches!(
                    url.host_str(),
                    Some("microsoft.com" | "www.microsoft.com" | "login.live.com" | "aka.ms")
                )
        })
        .ok_or("mc_service")?;
    let interval = Duration::from_secs(data["interval"].as_u64().unwrap_or(5).clamp(1, 60));
    let now = Instant::now();
    Ok(Device {
        code: required(&data, "user_code")?.to_owned(),
        secret: required(&data, "device_code")?.to_owned(),
        url: url.to_string(),
        cookies,
        interval,
        failed_polls: 0,
        next_poll: now + interval,
        deadline: now + Duration::from_secs(data["expires_in"].as_u64().unwrap_or(900).min(1800)),
    })
}

fn unix_time() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

fn xbox(
    client: &Client,
    key: &SigningKey,
    url: &str,
    payload: Value,
    stage: &str,
) -> Result<Value> {
    let bytes = serde_json::to_vec(&payload).map_err(|_| "mc_service")?;
    let timestamp = (unix_time() + 11_644_473_600) * 10_000_000;
    let path = Url::parse(url).map_err(|_| "mc_service")?;
    let mut input = 1u32.to_be_bytes().to_vec();
    input.push(0);
    input.extend(timestamp.to_be_bytes());
    input.push(0);
    for part in [
        b"POST".as_slice(),
        path.path().as_bytes(),
        b"".as_slice(),
        &bytes,
    ] {
        input.extend(part);
        input.push(0);
    }
    let signature: Signature = key.sign(&input);
    let mut header = 1u32.to_be_bytes().to_vec();
    header.extend(timestamp.to_be_bytes());
    header.extend(signature.to_bytes());
    let response = send(
        client
            .post(url)
            .header("Signature", STANDARD.encode(header))
            .header("x-xbl-contract-version", "1")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .body(bytes),
        stage,
    )?;
    let success = response.status().is_success();
    let data = body(response, stage)?;
    if success {
        return Ok(data);
    }
    Err(match data["XErr"].as_u64() {
        Some(2148916233) => "mc_xbox_profile",
        Some(2148916238 | 2148916229) => "mc_xbox_family",
        Some(2148916234) => "mc_xbox_terms",
        _ => "mc_service",
    }
    .into())
}

fn skin_url(value: &str) -> Option<String> {
    let url = Url::parse(value).ok()?;
    let texture = url.path().strip_prefix("/texture/")?;
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str() != Some("textures.minecraft.net")
        || texture.is_empty()
        || texture.len() > 64
        || !texture.bytes().all(|b| b.is_ascii_hexdigit())
    {
        return None;
    }
    Some(format!("https://textures.minecraft.net/texture/{texture}"))
}

fn finish(client: &Client, token: Value, stage: impl Fn(&str) -> Result<()>) -> Result<Account> {
    let access = required(&token, "access_token")?;
    let refresh = required(&token, "refresh_token")?;
    let key = SigningKey::random(&mut p256::elliptic_curve::rand_core::OsRng);
    let point = key.verifying_key().to_encoded_point(false);
    let proof = json!({"kty":"EC","crv":"P-256","alg":"ES256","use":"sig",
        "x":URL_SAFE_NO_PAD.encode(point.x().ok_or("mc_service")?),
        "y":URL_SAFE_NO_PAD.encode(point.y().ok_or("mc_service")?)});
    stage("xbox_device")?;
    let device = xbox(
        client,
        &key,
        "https://device.auth.xboxlive.com/device/authenticate",
        json!({"Properties":{"AuthMethod":"ProofOfPossession","Id":format!("{{{}}}",uuid::Uuid::new_v4()),
        "DeviceType":"Win32","Version":"10.0.0","ProofKey":proof},
        "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}),
        "xbox_device",
    )?;
    stage("xbox_login")?;
    let sisu = xbox(
        client,
        &key,
        "https://sisu.xboxlive.com/authorize",
        json!({"AccessToken":format!("t={access}"),"AppId":TITLE,"DeviceToken":required(&device,"Token")?,
        "Sandbox":"RETAIL","UseModernGamertag":true,"SiteName":"user.auth.xboxlive.com",
        "RelyingParty":"rp://api.minecraftservices.com/","ProofKey":proof}),
        "xbox_login",
    )?;
    let auth = &sisu["AuthorizationToken"];
    let hash = required(&auth["DisplayClaims"]["xui"][0], "uhs")?;
    let xbox_token = required(auth, "Token")?;
    stage("minecraft_login")?;
    let mc = checked(
        send(
            client
                .post(format!("{MINECRAFT}/authentication/login_with_xbox"))
                .json(&json!({"identityToken":format!("XBL3.0 x={hash};{xbox_token}")})),
            "minecraft_login",
        )?,
        "minecraft_login",
    )?;
    let access = required(&mc, "access_token")?;
    stage("minecraft_profile")?;
    let (entitlements, reply) = std::thread::scope(|scope| {
        let profile = scope.spawn(|| {
            send(
                client
                    .get(format!("{MINECRAFT}/minecraft/profile"))
                    .bearer_auth(access),
                "minecraft_profile",
            )
        });
        let entitlements = send(
            client
                .get(format!("{MINECRAFT}/entitlements/mcstore"))
                .bearer_auth(access),
            "minecraft_profile",
        )
        .and_then(|response| checked(response, "minecraft_profile"));
        (
            entitlements,
            profile.join().unwrap_or_else(|_| Err("mc_service".into())),
        )
    });
    let entitlements = entitlements?;
    if entitlements["items"].as_array().is_none_or(Vec::is_empty) {
        return Err("mc_license".into());
    }
    let reply = reply?;
    if reply.status().as_u16() == 404 {
        return Err("mc_java_profile".into());
    }
    let profile = checked(reply, "minecraft_profile")?;
    let id = required(&profile, "id")?;
    let name = required(&profile, "name")?;
    if id.len() != 32
        || !id.bytes().all(|b| b.is_ascii_hexdigit())
        || name.len() > 16
        || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
    {
        return Err("mc_service".into());
    }
    let active = profile["skins"]
        .as_array()
        .and_then(|skins| skins.iter().find(|skin| skin["state"] == "ACTIVE"));
    Ok(Account {
        profile: Profile {
            id: id.to_owned(),
            name: name.to_owned(),
            skin_url: active
                .and_then(|skin| skin["url"].as_str())
                .and_then(skin_url),
            slim: active.is_some_and(|skin| skin["variant"] == "SLIM"),
        },
        access_token: access.to_owned(),
        refresh_token: refresh.to_owned(),
        expires_at: unix_time().saturating_add(mc["expires_in"].as_u64().unwrap_or(3600)),
    })
}

#[tauri::command]
pub async fn minecraft_account(
    app: tauri::AppHandle,
    minecraft: tauri::State<'_, Minecraft>,
    action: String,
    id: Option<String>,
) -> Result<Value> {
    let state = minecraft.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        let path = accounts_path(&app)?;
        let load = || load_accounts(&path);
        let save = |accounts: &Accounts| save_accounts(&path, accounts);
        match action.as_str() {
            "list" => {
                let _storage = state.storage.lock().map_err(|_| "storage")?;
                return Ok(load()?.public());
            }
            "cancel" => {
                let mut login = state.login.lock().map_err(|_| "mc_service")?;
                login.generation += 1;
                login.device = None;
                return Ok(Value::Null);
            }
            "open" => {
                let url = state.login.lock().map_err(|_| "mc_service")?
                    .device.as_ref().ok_or("mc_expired")?.url.clone();
                super::open_browser(&url)?;
                return Ok(Value::Null);
            }
            "select" => {
                let _storage = state.storage.lock().map_err(|_| "storage")?;
                let mut accounts = load()?;
                let id = id
                    .filter(|id| accounts.accounts.iter().any(|a| a.profile.id == *id))
                    .ok_or("mc_service")?;
                accounts.selected = Some(id);
                save(&accounts)?;
                return Ok(accounts.public());
            }
            "start" | "poll" => {}
            _ => return Err("mc_service".into()),
        }
        let client = state.client.as_ref().as_ref().map_err(Clone::clone)?;
        if action == "start" {
            let generation = {
                let mut login = state.login.lock().map_err(|_| "mc_service")?;
                login.generation += 1;
                login.device = None;
                login.generation
            };
            let device = begin(client)?;
            let mut login = state.login.lock().map_err(|_| "mc_service")?;
            if generation != login.generation {
                return Err("mc_cancelled".into());
            }
            let data = json!({"code":device.code,"interval":device.interval.as_secs(),"generation":generation});
            login.device = Some(device);
            return Ok(data);
        }
        let Ok(_poll) = state.poll.try_lock() else {
            return Ok(json!({"pending":true,"interval":2}));
        };
        let (generation, device) = {
            let mut login = state.login.lock().map_err(|_| "mc_service")?;
            let generation = login.generation;
            let device = login.device.as_mut().ok_or("mc_expired")?;
            if Instant::now() >= device.deadline {
                login.device = None;
                return Err("mc_expired".into());
            }
            if Instant::now() < device.next_poll {
                return Ok(json!({"pending":true,"interval":device.interval.as_secs()}));
            }
            device.next_poll = Instant::now() + device.interval;
            (generation, device.clone())
        };
        let stage = |name: &str| -> Result<()> {
            if state.login.lock().map_err(|_| "mc_service")?.generation != generation {
                return Err("mc_cancelled".into());
            }
            let _ = app.emit("minecraft-login-stage", json!({"generation":generation,"stage":name}));
            Ok(())
        };
        let reply = send(
            client
                .post(TOKEN_URL)
                .header(reqwest::header::COOKIE, &device.cookies)
                .form(&[
                    ("client_id", TITLE),
                    ("device_code", device.secret.as_str()),
                    ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ]), "microsoft_token",
        );
        let response = reply.and_then(|reply| {
            let success = reply.status().is_success();
            if matches!(reply.status().as_u16(), 502 | 503 | 504) {
                return Err("mc_network:microsoft_token".into());
            }
            Ok((success, body(reply, "microsoft_token")?))
        });
        let (success, data) = match response {
            Ok(response) => response,
            Err(error) if (error.starts_with("mc_network:") || error.starts_with("mc_timeout:")) && Instant::now() < device.deadline => {
                let mut login = state.login.lock().map_err(|_| "mc_service")?;
                if login.generation != generation { return Err("mc_cancelled".into()); }
                let active = login.device.as_mut().ok_or("mc_expired")?;
                active.failed_polls += 1;
                if active.failed_polls >= 3 { return Err(error); }
                let wait = active.interval.max(Duration::from_secs(5));
                active.next_poll = Instant::now() + wait;
                return Ok(json!({"pending":true,"interval":wait.as_secs(),"retrying":true}));
            }
            Err(error) => return Err(error),
        };
        let mut login = state.login.lock().map_err(|_| "mc_service")?;
        if login.generation != generation { return Err("mc_cancelled".into()); }
        let active = login.device.as_mut().ok_or("mc_expired")?;
        active.failed_polls = 0;
        if !success {
            match data["error"].as_str() {
                Some("authorization_pending") => {
                    return Ok(json!({"pending":true,"interval":device.interval.as_secs()}))
                }
                Some("slow_down") => {
                    active.interval =
                        (active.interval + Duration::from_secs(5)).min(Duration::from_secs(60));
                    active.next_poll = Instant::now() + active.interval;
                    return Ok(json!({"pending":true,"interval":active.interval.as_secs()}));
                }
                Some("expired_token") => {
                    login.device = None;
                    return Err("mc_expired".into());
                }
                _ => {
                    login.device = None;
                    return Err("mc_denied".into());
                }
            }
        }
        login.device = None;
        drop(login);
        let account = finish(client, data, stage)?;
        let login = state.login.lock().map_err(|_| "mc_service")?;
        if generation != login.generation {
            return Err("mc_cancelled".into());
        }
        let _storage = state.storage.lock().map_err(|_| "storage")?;
        let mut accounts = load()?;
        accounts
            .accounts
            .retain(|old| old.profile.id != account.profile.id);
        accounts.selected = Some(account.profile.id.clone());
        accounts.accounts.push(account);
        save(&accounts)?;
        Ok(accounts.public())
    })
    .await
    .map_err(|_| "mc_service".to_owned())?
}
