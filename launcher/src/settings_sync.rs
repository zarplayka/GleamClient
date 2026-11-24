use crate::auth::GameAuth;
use reqwest::{blocking::Response, Method, StatusCode};
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use serde_json::{Map, Value};
use std::{
    fs::{self, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
    time::Duration,
};

const MINECRAFT_LIMIT: u64 = 128 * 1024;
const MODULES_LIMIT: u64 = 256 * 1024;
const RESPONSE_LIMIT: u64 = 512 * 1024;
const LOCAL_OPTIONS: &[&str] = &[
    "fullscreen",
    "fullscreenResolution",
    "overrideWidth",
    "overrideHeight",
    "resourcePacks",
    "incompatibleResourcePacks",
    "lastServer",
];
const LOCAL_MODULE_DATA: &[&str] = &["waypoints", "deathPoint", "hitSoundFile"];
type Result<T> = std::result::Result<T, &'static str>;

#[derive(Clone, Default, Deserialize, Serialize, PartialEq, Eq)]
struct Settings {
    minecraft: Option<String>,
    modules: Option<String>,
}

#[derive(Default, Deserialize, Serialize)]
struct Profile {
    revision: u64,
    synced: Settings,
    local: Settings,
}

#[derive(Deserialize, Serialize)]
struct Cloud {
    account_id: u64,
    revision: u64,
    #[serde(flatten)]
    settings: Settings,
}

#[derive(Serialize)]
struct Save<'a> {
    revision: u64,
    #[serde(flatten)]
    settings: &'a Settings,
}

pub(crate) struct SettingsSync {
    auth: GameAuth,
    account: u64,
    profile: Profile,
    directory: PathBuf,
    conflict: bool,
}

struct GameFiles {
    options_path: PathBuf,
    modules_path: PathBuf,
    original_options: Option<String>,
    original_modules: Option<String>,
    machine_options: Vec<String>,
    local_module_data: Map<String, Value>,
    portable: Settings,
}

fn read_text(path: &Path, limit: u64) -> Result<Option<String>> {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(_) => return Err("settings_storage"),
    };
    let mut text = String::new();
    file.take(limit + 1)
        .read_to_string(&mut text)
        .map_err(|_| "settings_storage")?;
    if text.len() as u64 > limit {
        return Err("settings_storage");
    }
    Ok(Some(text))
}

fn read_json<T: DeserializeOwned>(path: &Path) -> Result<Option<T>> {
    read_text(path, RESPONSE_LIMIT * 2)?
        .map(|text| serde_json::from_str(&text).map_err(|_| "settings_storage"))
        .transpose()
}

fn write_file(path: &Path, text: Option<&str>) -> Result<()> {
    let Some(text) = text else {
        return match fs::remove_file(path) {
            Ok(()) => Ok(()),
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
            Err(_) => Err("settings_storage"),
        };
    };
    let parent = path.parent().ok_or("settings_storage")?;
    fs::create_dir_all(parent).map_err(|_| "settings_storage")?;
    let mut temporary = tempfile::NamedTempFile::new_in(parent).map_err(|_| "settings_storage")?;
    temporary
        .write_all(text.as_bytes())
        .map_err(|_| "settings_storage")?;
    temporary.persist(path).map_err(|_| "settings_storage")?;
    Ok(())
}

fn write_json(path: &Path, value: &impl Serialize) -> Result<()> {
    let data = serde_json::to_string(value).map_err(|_| "settings_storage")?;
    write_file(path, Some(&data))
}

fn options_lines(text: &str) -> impl Iterator<Item = (&str, &str)> {
    text.lines().filter_map(|line| {
        let (name, _) = line.split_once(':')?;
        (!name.is_empty()).then_some((name, line))
    })
}

fn object(text: &str) -> Result<Map<String, Value>> {
    serde_json::from_str(text).map_err(|_| "settings_storage")
}

impl GameFiles {
    fn read(game_dir: &Path) -> Result<Self> {
        let options_path = game_dir.join("options.txt");
        let modules_path = game_dir.join("config").join("orion-client.json");
        let original_options = read_text(&options_path, MINECRAFT_LIMIT)?;
        let original_modules = read_text(&modules_path, MODULES_LIMIT)?;
        let (machine_options, mut portable_options): (Vec<_>, Vec<_>) =
            options_lines(original_options.as_deref().unwrap_or(""))
                .partition(|(name, _)| LOCAL_OPTIONS.contains(name));
        portable_options.sort_unstable_by_key(|(name, _)| *name);
        let minecraft = (!portable_options.is_empty()).then(|| {
            portable_options
                .iter()
                .map(|(_, line)| *line)
                .collect::<Vec<_>>()
                .join("\n")
                + "\n"
        });
        let mut modules = original_modules
            .as_deref()
            .map(object)
            .transpose()?
            .unwrap_or_default();
        let local_module_data = LOCAL_MODULE_DATA
            .iter()
            .filter_map(|name| modules.remove(*name).map(|value| (name.to_string(), value)))
            .collect();
        let modules = (!modules.is_empty()).then(|| Value::Object(modules).to_string());
        let machine_options = machine_options
            .iter()
            .map(|(_, line)| (*line).to_owned())
            .collect();
        Ok(Self {
            options_path,
            modules_path,
            original_options,
            original_modules,
            machine_options,
            local_module_data,
            portable: Settings { minecraft, modules },
        })
    }

    fn activate(&self, settings: &Settings, marker: &Path, account: u64) -> Result<()> {
        let change_options = self.portable.minecraft != settings.minecraft;
        let change_modules = self.portable.modules != settings.modules;
        let result = (|| {
            if change_options {
                let mut lines: Vec<_> = options_lines(settings.minecraft.as_deref().unwrap_or(""))
                    .filter(|(name, _)| !LOCAL_OPTIONS.contains(name))
                    .map(|(_, line)| line)
                    .collect();
                lines.extend(self.machine_options.iter().map(String::as_str));
                let text = (!lines.is_empty()).then(|| lines.join("\n") + "\n");
                write_file(&self.options_path, text.as_deref())?;
            }
            if change_modules {
                let mut modules = settings
                    .modules
                    .as_deref()
                    .map(object)
                    .transpose()?
                    .unwrap_or_default();
                for key in LOCAL_MODULE_DATA {
                    modules.remove(*key);
                }
                modules.extend(self.local_module_data.clone());
                let text = (!modules.is_empty()).then(|| Value::Object(modules).to_string());
                write_file(&self.modules_path, text.as_deref())?;
            }
            write_json(marker, &account)
        })();
        if result.is_err() {
            if change_options {
                let _ = write_file(&self.options_path, self.original_options.as_deref());
            }
            if change_modules {
                let _ = write_file(&self.modules_path, self.original_modules.as_deref());
            }
        }
        result
    }
}

fn merge_field(
    base: &Option<String>,
    local: &Option<String>,
    remote: &Option<String>,
) -> (Option<String>, bool) {
    if local == base {
        return (remote.clone(), false);
    }
    if remote == base || remote == local {
        return (local.clone(), false);
    }
    (remote.clone(), true)
}

fn decode_cloud(response: Response) -> Result<Cloud> {
    let response = response
        .error_for_status()
        .map_err(|_| "settings_network")?;
    let mut bytes = Vec::new();
    response
        .take(RESPONSE_LIMIT + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| "settings_network")?;
    if bytes.len() as u64 > RESPONSE_LIMIT {
        return Err("settings_network");
    }
    let cloud: Cloud = serde_json::from_slice(&bytes).map_err(|_| "settings_network")?;
    if cloud.account_id == 0
        || cloud
            .settings
            .minecraft
            .as_ref()
            .is_some_and(|text| text.len() as u64 > MINECRAFT_LIMIT)
        || cloud
            .settings
            .modules
            .as_ref()
            .is_some_and(|text| text.len() as u64 > MODULES_LIMIT || object(text).is_err())
    {
        return Err("settings_network");
    }
    Ok(cloud)
}

fn profile_path(directory: &Path, account: u64) -> PathBuf {
    directory.join(format!("{account}.json"))
}

fn save_conflict(directory: &Path, account: u64, local: &Settings) -> Result<()> {
    fs::create_dir_all(directory).map_err(|_| "settings_storage")?;
    let mut file = tempfile::Builder::new()
        .prefix(&format!("backup-{account}-"))
        .suffix(".json")
        .tempfile_in(directory)
        .map_err(|_| "settings_storage")?;
    serde_json::to_writer(&mut file, local).map_err(|_| "settings_storage")?;
    file.keep().map_err(|_| "settings_storage")?;
    Ok(())
}

fn activate_profile(
    game_dir: &Path,
    account: u64,
    remote: Option<Cloud>,
) -> Result<(PathBuf, Profile)> {
    let directory = game_dir.join("gleam").join("settings");
    let marker = directory.join("active.json");
    let active = read_json::<u64>(&marker)?;
    let files = GameFiles::read(game_dir)?;
    if let Some(previous) = active.filter(|previous| *previous != account) {
        let path = profile_path(&directory, previous);
        let mut saved: Profile = read_json(&path)?.unwrap_or_default();
        saved.local = files.portable.clone();
        write_json(&path, &saved)?;
    }
    let path = profile_path(&directory, account);
    let cached: Option<Profile> = read_json(&path)?;
    let empty_cloud = remote.as_ref().is_some_and(|cloud| {
        cloud.revision == 0 && cloud.settings == Settings::default()
    });
    let initialize_from_instance = empty_cloud && cached.is_none();
    let mut profile = cached.unwrap_or_default();
    if active.is_none() || active == Some(account) || initialize_from_instance {
        profile.local = files.portable.clone();
    }
    if let Some(cloud) = remote.filter(|_| !empty_cloud) {
        let (minecraft, mc_conflict) = merge_field(
            &profile.synced.minecraft,
            &profile.local.minecraft,
            &cloud.settings.minecraft,
        );
        let (modules, module_conflict) = merge_field(
            &profile.synced.modules,
            &profile.local.modules,
            &cloud.settings.modules,
        );
        if mc_conflict || module_conflict {
            save_conflict(&directory, account, &profile.local)?;
        }
        profile.local = Settings { minecraft, modules };
        profile.revision = cloud.revision;
        profile.synced = cloud.settings;
    } else if empty_cloud {
        // A new account or a restored server has no baseline to merge against.
        profile.revision = 0;
        profile.synced = Settings::default();
    }
    write_json(&path, &profile)?;
    files.activate(&profile.local, &marker, account)?;
    Ok((directory, profile))
}

impl SettingsSync {
    pub(crate) fn prepare(auth: GameAuth, game_dir: &Path) -> Result<Option<Self>> {
        let remote = auth
            .settings_request(Method::GET)
            .send()
            .ok()
            .and_then(|reply| decode_cloud(reply).ok());
        let Some(account) = remote
            .as_ref()
            .map(|cloud| cloud.account_id)
            .or(auth.account_id)
        else {
            return Ok(None);
        };
        if auth.account_id.is_some_and(|known| known != account) {
            return Err("settings_network");
        }
        let (directory, profile) = activate_profile(game_dir, account, remote)?;
        Ok(Some(Self {
            auth,
            account,
            profile,
            directory,
            conflict: false,
        }))
    }

    pub(crate) fn save(&mut self, game_dir: &Path, final_save: bool) -> Result<()> {
        if !final_save
            && ["options.txt", "config/orion-client.json"]
                .iter()
                .any(|path| {
                    fs::metadata(game_dir.join(path))
                        .and_then(|entry| entry.modified())
                        .is_ok_and(|modified| {
                            modified
                                .elapsed()
                                .is_ok_and(|age| age < Duration::from_millis(500))
                        })
                })
        {
            return Ok(());
        }
        let files = GameFiles::read(game_dir)?;
        let path = profile_path(&self.directory, self.account);
        if self.profile.local != files.portable {
            self.profile.local = files.portable;
            write_json(&path, &self.profile)?;
        }
        if self.conflict || self.profile.local == self.profile.synced {
            return Ok(());
        }
        let reply = self
            .auth
            .settings_request(Method::POST)
            .json(&Save {
                revision: self.profile.revision,
                settings: &self.profile.local,
            })
            .send()
            .map_err(|_| "settings_network")?;
        if reply.status() == StatusCode::CONFLICT {
            self.conflict = true;
            return Ok(());
        }
        let cloud = decode_cloud(reply)?;
        if cloud.account_id != self.account {
            return Err("settings_network");
        }
        self.profile.revision = cloud.revision;
        self.profile.synced = cloud.settings;
        write_json(&path, &self.profile)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn restores_portable_settings_and_keeps_device_data() {
        let game = tempfile::tempdir().unwrap();
        let dir = game.path();
        write_file(
            &dir.join("options.txt"),
            Some("lang:ru_ru\nfullscreen:true\n"),
        )
        .unwrap();
        write_file(
            &dir.join("config/orion-client.json"),
            Some(r#"{"zoomEnabled":false,"waypoints":[{"name":"home"}]}"#),
        )
        .unwrap();
        let files = GameFiles::read(dir).unwrap();
        assert_eq!(files.portable.minecraft.as_deref(), Some("lang:ru_ru\n"));
        assert_eq!(
            files.portable.modules.as_deref(),
            Some(r#"{"zoomEnabled":false}"#)
        );
        files
            .activate(
                &Settings {
                    minecraft: Some("lang:en_us\nfullscreen:false\n".into()),
                    modules: Some(r#"{"zoomEnabled":true,"waypoints":[]}"#.into()),
                },
                &dir.join("active.json"),
                42,
            )
            .unwrap();
        assert_eq!(
            read_json::<u64>(&dir.join("active.json")).unwrap(),
            Some(42)
        );
        assert_eq!(
            fs::read_to_string(dir.join("options.txt")).unwrap(),
            "lang:en_us\nfullscreen:true\n"
        );
        let modules =
            object(&fs::read_to_string(dir.join("config/orion-client.json")).unwrap()).unwrap();
        assert_eq!(modules["zoomEnabled"], true);
        assert_eq!(modules["waypoints"][0]["name"], "home");
    }

    #[test]
    fn merges_independent_changes_and_detects_conflicts() {
        let base = Some("base".to_string());
        let local = Some("local".to_string());
        let remote = Some("remote".to_string());
        assert_eq!(merge_field(&base, &local, &base), (local.clone(), false));
        assert_eq!(merge_field(&base, &base, &remote), (remote.clone(), false));
        assert_eq!(merge_field(&base, &local, &remote), (remote, true));
    }

    #[test]
    fn switching_accounts_restores_their_own_settings_offline() {
        let game = tempfile::tempdir().unwrap();
        let dir = game.path();
        let options = dir.join("options.txt");
        write_file(&options, Some("lang:ru_ru\n")).unwrap();
        activate_profile(dir, 1, None).unwrap();
        activate_profile(dir, 2, None).unwrap();
        assert!(!options.exists());
        write_file(&options, Some("lang:de_de\n")).unwrap();
        activate_profile(dir, 1, None).unwrap();
        assert_eq!(fs::read_to_string(&options).unwrap(), "lang:ru_ru\n");
        activate_profile(dir, 2, None).unwrap();
        assert_eq!(fs::read_to_string(&options).unwrap(), "lang:de_de\n");
    }

    #[test]
    fn first_sign_in_after_server_reset_keeps_the_current_instance_settings() {
        let game = tempfile::tempdir().unwrap();
        let dir = game.path();
        let options = dir.join("options.txt");
        let config = dir.join("config/orion-client.json");
        write_file(&options, Some("lang:ru_ru\nfullscreen:true\n")).unwrap();
        write_file(&config, Some(r#"{"zoomEnabled":true}"#)).unwrap();
        activate_profile(dir, 2, None).unwrap();

        let empty = Cloud { account_id: 1, revision: 0, settings: Settings::default() };
        let (directory, profile) = activate_profile(dir, 1, Some(empty)).unwrap();
        assert_eq!(fs::read_to_string(&options).unwrap(), "lang:ru_ru\nfullscreen:true\n");
        assert_eq!(fs::read_to_string(&config).unwrap(), r#"{"zoomEnabled":true}"#);
        assert_eq!(profile.local.minecraft.as_deref(), Some("lang:ru_ru\n"));
        assert!(profile.local != profile.synced);
        let previous: Profile = read_json(&profile_path(&directory, 2)).unwrap().unwrap();
        assert!(previous.local == profile.local);
    }

    #[test]
    fn empty_cloud_after_restore_does_not_remove_saved_settings() {
        let game = tempfile::tempdir().unwrap();
        let dir = game.path();
        let options = dir.join("options.txt");
        write_file(&options, Some("lang:ru_ru\n")).unwrap();
        let cloud = |revision, minecraft| Cloud {
            account_id: 1,
            revision,
            settings: Settings { minecraft, modules: None },
        };
        activate_profile(dir, 1, Some(cloud(4, Some("lang:ru_ru\n".into())))).unwrap();
        let (_, profile) = activate_profile(dir, 1, Some(cloud(0, None))).unwrap();
        assert_eq!(profile.revision, 0);
        assert!(profile.synced == Settings::default());
        assert_eq!(fs::read_to_string(&options).unwrap(), "lang:ru_ru\n");
    }

    #[test]
    fn a_second_game_directory_receives_the_account_settings_before_launch() {
        let source = tempfile::tempdir().unwrap();
        let target = tempfile::tempdir().unwrap();
        write_file(&source.path().join("options.txt"), Some("lang:ru_ru\n")).unwrap();
        write_file(
            &source.path().join("config/orion-client.json"),
            Some(r#"{"zoomEnabled":true}"#),
        )
        .unwrap();
        let (_, first_profile) = activate_profile(source.path(), 42, None).unwrap();
        let cloud = Cloud {
            account_id: 42,
            revision: 1,
            settings: first_profile.local.clone(),
        };
        let (_, restored) = activate_profile(target.path(), 42, Some(cloud)).unwrap();
        assert_eq!(restored.revision, 1);
        assert!(restored.local == restored.synced);
        assert!(GameFiles::read(target.path()).unwrap().portable == first_profile.local);
    }
}
