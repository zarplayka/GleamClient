use reqwest::blocking::{Client, RequestBuilder};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha512};
use std::{
    collections::{BTreeMap, HashSet},
    fs,
    io::{Read, Write},
    path::{Component, Path, PathBuf},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tauri::{Emitter, Manager};

const PAGE_SIZE: u32 = 20;
const MAX_FILE_SIZE: u64 = 128 * 1024 * 1024;
const GAME_VERSIONS: [&str; 6] = ["1.16.5", "1.21.4", "1.21.11", "26.1.2", "26.2", "26.3"];

#[derive(Clone)]
pub struct Catalog {
    client: Arc<Result<Client, String>>,
    installation: Arc<Mutex<()>>,
}

impl Catalog {
    pub fn new() -> Self {
        Self {
            client: Arc::new(
                Client::builder()
                    .user_agent("GleamLauncher/0.1.0 (https://gleamclient.xyz)")
                    .connect_timeout(Duration::from_secs(5))
                    .timeout(Duration::from_secs(60))
                    .redirect(reqwest::redirect::Policy::none())
                    .build()
                    .map_err(|_| "Не удалось создать сетевое подключение".into()),
            ),
            installation: Arc::new(Mutex::new(())),
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Project {
    id: String,
    title: String,
    description: String,
    author: String,
    icon: String,
    url: String,
    downloads: u64,
    installed: bool,
}

#[derive(Serialize)]
pub struct Page {
    projects: Vec<Project>,
    total: u64,
}

#[derive(Deserialize, Serialize)]
struct InstalledFile {
    name: String,
    sha512: String,
}

#[derive(Deserialize, Serialize)]
struct Receipt {
    projects: Vec<String>,
    files: Vec<InstalledFile>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct Progress {
    provider: String,
    project: String,
    game_version: String,
    title: String,
    percent: u32,
    bytes_per_second: u64,
}

struct Artifact {
    project: String,
    name: String,
    url: String,
    size: u64,
    algorithm: &'static str,
    hash: String,
}

fn validate(provider: &str, version: &str) -> Result<(), String> {
    if !matches!(provider, "modrinth" | "curseforge") {
        return Err("Неизвестный каталог".into());
    }
    validate_version(version)
}

fn validate_version(version: &str) -> Result<(), String> {
    if !GAME_VERSIONS.contains(&version) {
        return Err("Выбери версию Minecraft".into());
    }
    Ok(())
}

fn validate_id(provider: &str, id: &str) -> Result<(), String> {
    let valid = !id.is_empty()
        && id.len() <= 80
        && id.bytes().all(|ch| {
            if provider == "curseforge" {
                ch.is_ascii_digit()
            } else {
                ch.is_ascii_alphanumeric() || matches!(ch, b'-' | b'_')
            }
        });
    if valid {
        Ok(())
    } else {
        Err("Некорректный идентификатор мода".into())
    }
}

fn text(value: &Value, field: &str) -> String {
    value[field].as_str().unwrap_or_default().to_string()
}

fn io_error(_: std::io::Error) -> String {
    "Не удалось прочитать или сохранить файлы модов".into()
}

fn json_response(request: RequestBuilder) -> Result<Value, String> {
    let response = request
        .timeout(Duration::from_secs(12))
        .send()
        .map_err(|error| {
            if error.is_timeout() {
                "Каталог не ответил вовремя".to_string()
            } else {
                "Сервис недоступен. Проверь подключение".to_string()
            }
        })?;
    let status = response.status();
    if !status.is_success() {
        return Err(match status.as_u16() {
            401 | 403 => "Сервис отклонил API-ключ или доступ к проекту".into(),
            404 => "Проект или совместимая версия не найдены".into(),
            429 => "Слишком много запросов. Повтори чуть позже".into(),
            _ => format!("Каталог временно недоступен (HTTP {})", status.as_u16()),
        });
    }
    let mut bytes = Vec::new();
    response
        .take(4 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(io_error)?;
    if bytes.len() > 4 * 1024 * 1024 {
        return Err("Ответ каталога слишком большой".into());
    }
    serde_json::from_slice(&bytes).map_err(|_| "Каталог вернул некорректный ответ".into())
}

fn api(
    client: &Client,
    app: &tauri::AppHandle,
    provider: &str,
    path: &str,
) -> Result<RequestBuilder, String> {
    if provider == "modrinth" {
        return Ok(client.get(format!("https://api.modrinth.com/v2/{path}")));
    }
    let config = app
        .path()
        .app_config_dir()
        .map_err(|_| "Папка настроек недоступна")?;
    let keys: Value = match fs::read(config.join("catalog-keys.json")) {
        Ok(bytes) => serde_json::from_slice(&bytes).map_err(|_| "Не удалось прочитать ключ CurseForge")?,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Value::Null,
        Err(_) => return Err("Не удалось прочитать ключ CurseForge".into()),
    };
    let key = keys["curseforge"]
        .as_str()
        .map(str::trim)
        .filter(|key| !key.is_empty())
        .or_else(|| {
            let embedded = include_str!(concat!(env!("OUT_DIR"), "/curseforge-key.txt"));
            (!embedded.is_empty()).then_some(embedded)
        })
        .ok_or("Не настроен ключ CurseForge")?;
    Ok(client
        .get(format!("https://api.curseforge.com/v1/{path}"))
        .header("x-api-key", key))
}

pub(crate) fn instance_dir(app: &tauri::AppHandle, version: &str) -> Result<PathBuf, String> {
    Ok(instances_dir(app)?.join(version))
}

fn instances_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    Ok(app
        .path()
        .app_data_dir()
        .map_err(|_| "Папка лаунчера недоступна")?
        .join("instances"))
}

#[derive(Serialize)]
struct InstanceUsage {
    version: &'static str,
    bytes: u64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StorageUsage {
    total_bytes: u64,
    versions: Vec<InstanceUsage>,
}

fn directory_size(root: PathBuf) -> std::io::Result<u64> {
    let mut bytes = 0u64;
    let mut pending = vec![root];
    while let Some(directory) = pending.pop() {
        let entries = match fs::read_dir(directory) {
            Ok(entries) => entries,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(error) => return Err(error),
        };
        for entry in entries {
            let entry = entry?;
            let metadata = match entry.metadata() {
                Ok(metadata) => metadata,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
                Err(error) => return Err(error),
            };
            if metadata.file_type().is_symlink() {
                continue;
            }
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                const REPARSE_POINT: u32 = 0x400;
                if metadata.file_attributes() & REPARSE_POINT != 0 {
                    continue;
                }
            }
            if metadata.is_dir() {
                pending.push(entry.path());
            } else if metadata.is_file() {
                bytes = bytes.saturating_add(metadata.len());
            }
        }
    }
    Ok(bytes)
}

#[tauri::command]
pub async fn instance_storage(app: tauri::AppHandle) -> Result<StorageUsage, String> {
    let root = instances_dir(&app)?;
    tauri::async_runtime::spawn_blocking(move || {
        let mut storage = StorageUsage {
            total_bytes: 0,
            versions: Vec::with_capacity(GAME_VERSIONS.len()),
        };
        for version in GAME_VERSIONS {
            let folder = root.join(version);
            fs::create_dir_all(&folder).map_err(|_| "Не удалось создать папку версии")?;
            let bytes = directory_size(folder).map_err(|_| "Не удалось проверить размер данных")?;
            storage.total_bytes = storage.total_bytes.saturating_add(bytes);
            storage.versions.push(InstanceUsage { version, bytes });
        }
        let imported = root.join("imported");
        if imported.is_dir() {
            storage.total_bytes = storage.total_bytes.saturating_add(
                directory_size(imported).map_err(|_| "Не удалось проверить размер данных")?
            );
        }
        Ok(storage)
    })
    .await
    .map_err(|_| "Не удалось проверить размер данных".to_string())?
}

#[tauri::command]
pub async fn open_instance_folder(
    app: tauri::AppHandle,
    game_version: String,
    instance_id: Option<String>,
) -> Result<(), String> {
    let folder = if let Some(id) = instance_id {
        crate::profile_import::installed(&app)?.into_iter().find(|(profile, _)| profile.id == id)
            .map(|(_, path)| path).ok_or("Профиль не найден")?
    } else {
        validate_version(&game_version)?;
        instance_dir(&app, &game_version)?
    };
    tauri::async_runtime::spawn_blocking(move || {
        fs::create_dir_all(&folder).map_err(|_| "Не удалось создать папку версии")?;
        open_local_path(&folder).map_err(|_| "Не удалось открыть папку версии".to_string())
    })
    .await
    .map_err(|_| "Не удалось открыть папку версии".to_string())?
}

pub(crate) fn open_local_path(path: &Path) -> Result<(), String> {
    let opener = match std::env::consts::OS {
        "windows" => "explorer.exe",
        "macos" => "open",
        _ => "xdg-open",
    };
    std::process::Command::new(opener).arg(path).spawn().map(|_| ())
        .map_err(|_| "Не удалось открыть файл или папку".to_string())
}

const COMMON_MODS: [(&str, &str, &str); 5] = [
    ("sodium", "AANobbMI", "394468"),
    ("lithium", "gvQqBUqZ", "360438"),
    ("entityculling", "NNAgCjsB", "448233"),
    ("immediatelyfast", "5ZwdcRci", "686911"),
    ("fabric-api", "P7dR8mSH", "306612"),
];

pub(crate) fn common_mod_id(project: &str) -> Option<&'static str> {
    COMMON_MODS.iter().find(|entry| entry.1 == project).map(|entry| entry.0)
}

pub(crate) fn installed_common_mods(instance: &Path) -> Vec<(&'static str, PathBuf)> {
    common_mods(instance, false)
}

pub(crate) fn disabled_common_mods(instance: &Path) -> Vec<(&'static str, PathBuf)> {
    common_mods(instance, true)
}

pub(crate) fn fabric_mod_metadata(path: &Path) -> Option<Value> {
    let mut jar = zip::ZipArchive::new(fs::File::open(path).ok()?).ok()?;
    let metadata = jar.by_name("fabric.mod.json").ok()?;
    if metadata.size() > 256 * 1024 {
        return None;
    }
    serde_json::from_reader(metadata.take(256 * 1024 + 1)).ok()
}

fn common_mods(instance: &Path, disabled: bool) -> Vec<(&'static str, PathBuf)> {
    let Ok(entries) = fs::read_dir(instance.join("mods")) else {
        return Vec::new();
    };
    entries.flatten().filter_map(|entry| {
        let path = entry.path();
        let name = path.file_name()?.to_str()?;
        let jar_name = if disabled { name.strip_suffix(".disabled")? } else { name };
        if !Path::new(jar_name).extension().is_some_and(|extension| extension.eq_ignore_ascii_case("jar")) || !path.is_file() {
            return None;
        }
        let metadata = fabric_mod_metadata(&path)?;
        let id = COMMON_MODS.iter().find(|entry| metadata["id"] == entry.0)?.0;
        Some((id, path))
    }).collect()
}

fn installed_projects(instance: &Path) -> HashSet<String> {
    let mut installed = HashSet::new();
    for (id, _) in installed_common_mods(instance).into_iter().chain(disabled_common_mods(instance)) {
        if let Some((_, modrinth, curseforge)) = COMMON_MODS.iter().find(|entry| entry.0 == id) {
            installed.insert(format!("modrinth:{modrinth}"));
            installed.insert(format!("curseforge:{curseforge}"));
        }
    }
    let Ok(entries) = fs::read_dir(instance.join("catalog")) else {
        return installed;
    };
    for entry in entries.flatten() {
        let receipt = fs::read(entry.path())
            .ok()
            .and_then(|bytes| serde_json::from_slice::<Receipt>(&bytes).ok());
        if let Some(receipt) = receipt {
            if !receipt.files.is_empty()
                && receipt.files.iter().all(|file| {
                    safe_filename(&file.name).is_ok()
                        && (instance.join("mods").join(&file.name).is_file()
                            || instance.join("mods").join(format!("{}.disabled", file.name)).is_file())
                })
            {
                installed.extend(receipt.projects);
            }
        }
    }
    installed
}

#[cfg(test)]
#[test]
fn renamed_optimizer_is_installed_in_both_catalogs() {
    let profile = tempfile::tempdir().unwrap();
    let mods = profile.path().join("mods");
    fs::create_dir(&mods).unwrap();
    let jar = fs::File::create(mods.join("my-renderer.jar")).unwrap();
    let mut jar = zip::ZipWriter::new(jar);
    jar.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
    jar.write_all(br#"{"id":"sodium"}"#).unwrap();
    jar.finish().unwrap();
    let installed = installed_projects(profile.path());
    assert!(installed.contains("modrinth:AANobbMI"));
    assert!(installed.contains("curseforge:394468"));
}

#[tauri::command]
pub async fn catalog_search(
    app: tauri::AppHandle,
    state: tauri::State<'_, Catalog>,
    provider: String,
    game_version: String,
    instance_id: Option<String>,
    query: String,
    sort: String,
    offset: u32,
    category: Option<String>,
) -> Result<Page, String> {
    validate(&provider, &game_version)?;
    let category = category.unwrap_or_default();
    let curse_category = match category.as_str() {
        "" => "0",
        "optimization" => "6814",
        "utility" => "5191",
        "library" => "421",
        "adventure" => "422",
        "decoration" => "424",
        "worldgen" => "406",
        "technology" => "412",
        "magic" => "419",
        _ => return Err("Неизвестная категория модов".into()),
    };
    if query.chars().count() > 120
        || offset > 9980
        || !matches!(sort.as_str(), "downloads" | "relevance" | "updated")
    {
        return Err("Некорректные параметры поиска".into());
    }
    let catalog = state.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        let client = catalog.client.as_ref().as_ref().map_err(Clone::clone)?;
        let installed = installed_projects(&crate::profile_import::instance(&app, &game_version, instance_id.as_deref())?);
        let mut projects = Vec::new();
        let total;
        if provider == "modrinth" {
            let mut facets = vec![
                vec!["project_type:mod".to_string()],
                vec!["categories:fabric".to_string()],
                vec![format!("versions:{game_version}")],
                vec![
                    "client_side:required".to_string(),
                    "client_side:optional".to_string(),
                ],
            ];
            if !category.is_empty() {
                facets.push(vec![format!("categories:{category}")]);
            }
            let response = json_response(api(client, &app, &provider, "search")?.query(&[
                ("query", query.trim().to_string()),
                ("facets", json!(facets).to_string()),
                ("index", sort),
                ("offset", offset.to_string()),
                ("limit", PAGE_SIZE.to_string()),
            ]))?;
            total = response["total_hits"].as_u64().unwrap_or_default();
            for hit in response["hits"].as_array().into_iter().flatten() {
                let id = text(hit, "project_id");
                validate_id(&provider, &id)?;
                projects.push(Project {
                    installed: installed.contains(&format!("{provider}:{id}")),
                    url: format!("https://modrinth.com/mod/{id}"),
                    id,
                    title: text(hit, "title"),
                    description: text(hit, "description"),
                    author: text(hit, "author"),
                    icon: text(hit, "icon_url"),
                    downloads: hit["downloads"].as_u64().unwrap_or_default(),
                });
            }
        } else {
            let order = match sort.as_str() {
                "updated" => "3",
                "relevance" => "1",
                _ => "6",
            };
            let mut request = api(client, &app, &provider, "mods/search")?.query(&[
                ("gameId", "432"),
                ("classId", "6"),
                ("modLoaderType", "4"),
                ("gameVersion", &game_version),
                ("searchFilter", query.trim()),
                ("sortField", order),
                ("sortOrder", "desc"),
                ("index", &offset.to_string()),
                ("pageSize", &PAGE_SIZE.to_string()),
            ]);
            if !category.is_empty() {
                request = request.query(&[("categoryId", curse_category)]);
            }
            let response = json_response(request)?;
            total = response["pagination"]["totalCount"]
                .as_u64()
                .unwrap_or_default()
                .min(10000);
            for hit in response["data"].as_array().into_iter().flatten() {
                let id = hit["id"]
                    .as_u64()
                    .ok_or("Некорректный ответ CurseForge")?
                    .to_string();
                projects.push(Project {
                    installed: installed.contains(&format!("{provider}:{id}")),
                    url: format!("https://www.curseforge.com/projects/{id}"),
                    id,
                    title: text(hit, "name"),
                    description: text(hit, "summary"),
                    author: hit["authors"]
                        .as_array()
                        .into_iter()
                        .flatten()
                        .map(|a| text(a, "name"))
                        .collect::<Vec<_>>()
                        .join(", "),
                    icon: text(&hit["logo"], "thumbnailUrl"),
                    downloads: hit["downloadCount"].as_f64().unwrap_or_default() as u64,
                });
            }
        }
        Ok(Page { projects, total })
    })
    .await
    .map_err(|_| "Не удалось выполнить поиск модов".to_string())?
}

fn safe_filename(name: &str) -> Result<(), String> {
    if name.is_empty()
        || name.len() > 180
        || !name.ends_with(".jar")
        || name
            .chars()
            .any(|ch| ch.is_control() || "\\/:*?\"<>|".contains(ch))
        || Path::new(name).components().count() != 1
        || !matches!(
            Path::new(name).components().next(),
            Some(Component::Normal(_))
        )
    {
        return Err("Каталог вернул некорректное имя файла".into());
    }
    Ok(())
}

fn resolve(
    client: &Client,
    app: &tauri::AppHandle,
    provider: &str,
    version: &str,
    root: &str,
) -> Result<Vec<Artifact>, String> {
    let mut queue = vec![(root.to_string(), None::<String>)];
    let mut resolved = BTreeMap::new();
    let mut artifacts = Vec::new();
    while let Some((project, pinned)) = queue.pop() {
        validate_id(provider, &project)?;
        if let Some(previous) = resolved.get(&project) {
            if pinned.as_ref().is_some_and(|id| id != previous) {
                return Err("У модов несовместимые обязательные зависимости".into());
            }
            continue;
        }
        if resolved.len() >= 40 {
            return Err("Слишком много зависимостей у мода".into());
        }
        let file;
        let file_id;
        if provider == "modrinth" {
            let release = if let Some(id) = pinned {
                validate_id(provider, &id)?;
                json_response(api(client, app, provider, &format!("version/{id}"))?)?
            } else {
                let versions = json_response(
                    api(client, app, provider, &format!("project/{project}/version"))?.query(&[
                        ("loaders", json!(["fabric"]).to_string()),
                        ("game_versions", json!([version]).to_string()),
                        ("include_changelog", "false".into()),
                    ]),
                )?;
                versions
                    .as_array()
                    .and_then(|items| items.first())
                    .cloned()
                    .ok_or("Для этой версии Minecraft нет совместимого Fabric-файла")?
            };
            if text(&release, "project_id") != project
                || !release["game_versions"]
                    .as_array()
                    .is_some_and(|items| items.iter().any(|v| v == version))
                || !release["loaders"]
                    .as_array()
                    .is_some_and(|items| items.iter().any(|v| v == "fabric"))
            {
                return Err(
                    "Зависимость не поддерживает выбранную версию Minecraft и Fabric".into(),
                );
            }
            file_id = text(&release, "id");
            let files = release["files"].as_array().ok_or("В релизе нет файлов")?;
            let artifact = files
                .iter()
                .find(|file| file["primary"] == true)
                .or_else(|| files.first())
                .ok_or("В релизе нет файлов")?;
            file = Artifact {
                project: project.clone(),
                name: text(artifact, "filename"),
                url: text(artifact, "url"),
                size: artifact["size"].as_u64().ok_or("Не указан размер файла")?,
                algorithm: "sha512",
                hash: text(&artifact["hashes"], "sha512"),
            };
            for dependency in release["dependencies"].as_array().into_iter().flatten() {
                if dependency["dependency_type"] == "required" {
                    let mut dependency_project = text(dependency, "project_id");
                    let dependency_version = dependency["version_id"].as_str().map(String::from);
                    if dependency_project.is_empty() {
                        let id = dependency_version
                            .as_deref()
                            .ok_or("Неизвестная обязательная зависимость")?;
                        validate_id(provider, id)?;
                        dependency_project = text(
                            &json_response(api(client, app, provider, &format!("version/{id}"))?)?,
                            "project_id",
                        );
                    }
                    queue.push((dependency_project, dependency_version));
                }
            }
        } else {
            let response = json_response(
                api(client, app, provider, &format!("mods/{project}/files"))?.query(&[
                    ("gameVersion", version),
                    ("modLoaderType", "4"),
                    ("pageSize", "1"),
                ]),
            )?;
            let artifact = response["data"]
                .as_array()
                .and_then(|items| items.first())
                .ok_or("Для этой версии Minecraft нет совместимого Fabric-файла")?;
            if !artifact["gameVersions"].as_array().is_some_and(|items| {
                items.iter().any(|v| v == version) && items.iter().any(|v| v == "Fabric")
            }) {
                return Err("Файл не поддерживает выбранную версию Minecraft и Fabric".into());
            }
            file_id = artifact["id"]
                .as_u64()
                .ok_or("Некорректный файл CurseForge")?
                .to_string();
            let mut url = text(artifact, "downloadUrl");
            if url.is_empty() {
                url = text(
                    &json_response(api(
                        client,
                        app,
                        provider,
                        &format!("mods/{project}/files/{file_id}/download-url"),
                    )?)?,
                    "data",
                );
            }
            if url.is_empty() {
                return Err("Автор отключил скачивание этого мода через сторонние лаунчеры".into());
            }
            let hash = artifact["hashes"]
                .as_array()
                .and_then(|hashes| hashes.iter().find(|hash| hash["algo"] == 1))
                .ok_or("Не указана контрольная сумма файла")?;
            file = Artifact {
                project: project.clone(),
                name: text(artifact, "fileName"),
                url,
                size: artifact["fileLength"]
                    .as_u64()
                    .ok_or("Не указан размер файла")?,
                algorithm: "sha1",
                hash: text(hash, "value"),
            };
            for dependency in artifact["dependencies"].as_array().into_iter().flatten() {
                if dependency["relationType"] == 3 {
                    let id = dependency["modId"]
                        .as_u64()
                        .ok_or("Неизвестная обязательная зависимость")?;
                    queue.push((id.to_string(), None));
                }
            }
        }
        safe_filename(&file.name)?;
        let url = reqwest::Url::parse(&file.url).map_err(|_| "Некорректный адрес файла")?;
        let host = url.host_str().unwrap_or_default();
        let allowed = if provider == "modrinth" {
            host == "cdn.modrinth.com"
        } else {
            host == "edge.forgecdn.net" || host == "mediafilez.forgecdn.net"
        };
        if url.scheme() != "https"
            || !allowed
            || file.size == 0
            || file.size > MAX_FILE_SIZE
            || file.hash.len() != if file.algorithm == "sha512" { 128 } else { 40 }
            || !file.hash.bytes().all(|ch| ch.is_ascii_hexdigit())
        {
            return Err("Каталог вернул непроверенные параметры скачивания".into());
        }
        resolved.insert(project, file_id);
        artifacts.push(file);
    }
    Ok(artifacts)
}

fn emit_progress(
    app: &tauri::AppHandle,
    provider: &str,
    project: &str,
    version: &str,
    title: &str,
    percent: u32,
    bytes_per_second: u64,
) {
    let _ = app.emit(
        "catalog-progress",
        Progress {
            provider: provider.into(),
            project: project.into(),
            game_version: version.into(),
            title: title.into(),
            percent,
            bytes_per_second,
        },
    );
}

fn file_hash(path: &Path, algorithm: &str) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(io_error)?;
    let mut sha512 = Sha512::new();
    let mut sha1 = sha1::Sha1::new();
    let mut buffer = [0u8; 65536];
    loop {
        let size = file.read(&mut buffer).map_err(io_error)?;
        if size == 0 {
            break;
        }
        sha512.update(&buffer[..size]);
        sha1.update(&buffer[..size]);
    }
    Ok(if algorithm == "sha512" {
        format!("{:x}", sha512.finalize())
    } else {
        format!("{:x}", sha1.finalize())
    })
}

fn download(
    client: &Client,
    artifact: &Artifact,
    path: &Path,
    mut progress: impl FnMut(u64, u64),
) -> Result<(), String> {
    let mut url = reqwest::Url::parse(&artifact.url).map_err(|_| "Некорректный адрес файла")?;
    for _ in 0..4 {
        if url.scheme() != "https"
            || !matches!(
                url.host_str(),
                Some("cdn.modrinth.com" | "edge.forgecdn.net" | "mediafilez.forgecdn.net")
            )
        {
            return Err("Неизвестный сервер скачивания".into());
        }
        let response = client
            .get(url.clone())
            .send()
            .map_err(|_| "Не удалось скачать мод. Проверь подключение")?;
        if response.status().is_redirection() {
            let location = response
                .headers()
                .get(reqwest::header::LOCATION)
                .and_then(|value| value.to_str().ok())
                .ok_or("Некорректный адрес скачивания")?;
            url = url
                .join(location)
                .map_err(|_| "Некорректный адрес скачивания")?;
            continue;
        }
        if !response.status().is_success() {
            return Err(format!(
                "Не удалось скачать файл (HTTP {})",
                response.status().as_u16()
            ));
        }
        let mut output = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(path)
            .map_err(io_error)?;
        let mut input = response.take(artifact.size + 1);
        let mut buffer = [0u8; 65536];
        let mut count = 0u64;
        let mut sampled_bytes = 0u64;
        let mut sampled_at = Instant::now();
        loop {
            let read = input.read(&mut buffer).map_err(io_error)?;
            if read == 0 {
                break;
            }
            output.write_all(&buffer[..read]).map_err(io_error)?;
            count += read as u64;
            let elapsed = sampled_at.elapsed();
            if elapsed >= Duration::from_millis(250) || count >= artifact.size {
                let speed =
                    ((count - sampled_bytes) as f64 / elapsed.as_secs_f64().max(0.001)) as u64;
                progress(count.min(artifact.size), speed);
                sampled_bytes = count;
                sampled_at = Instant::now();
            }
        }
        output.sync_all().map_err(io_error)?;
        if count != artifact.size
            || !file_hash(path, artifact.algorithm)?.eq_ignore_ascii_case(&artifact.hash)
        {
            return Err("Файл повреждён: контрольная сумма или размер не совпадают".into());
        }
        return Ok(());
    }
    Err("Сервис вернул слишком много перенаправлений".into())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Installation {
    pub count: usize,
    pub directory: String,
}

#[tauri::command]
pub async fn catalog_install(
    app: tauri::AppHandle,
    state: tauri::State<'_, Catalog>,
    provider: String,
    game_version: String,
    instance_id: Option<String>,
    project: String,
) -> Result<Installation, String> {
    validate(&provider, &game_version)?;
    validate_id(&provider, &project)?;
    let catalog = state.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = catalog
            .installation
            .try_lock()
            .map_err(|_| "Дождись завершения текущей установки")?;
        let client = catalog.client.as_ref().as_ref().map_err(Clone::clone)?;
        let instance = crate::profile_import::instance(&app, &game_version, instance_id.as_deref())?;
        fs::create_dir_all(&instance).map_err(io_error)?;
        let instance_lock = fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(false)
            .open(instance.join("catalog-install.lock"))
            .map_err(io_error)?;
        instance_lock
            .try_lock()
            .map_err(|_| "В другом окне лаунчера уже идёт установка для этой версии")?;
        let mods = instance.join("mods");
        let receipts = instance.join("catalog");
        let receipt_file = receipts.join(format!("{provider}-{project}.json"));
        emit_progress(
            &app,
            &provider,
            &project,
            &game_version,
            "Проверка зависимостей",
            0,
            0,
        );
        let installed = installed_projects(&instance);
        if installed.contains(&format!("{provider}:{project}")) {
            return Ok(Installation {
                count: 0,
                directory: mods.to_string_lossy().into(),
            });
        }
        let mut artifacts = resolve(client, &app, &provider, &game_version, &project)?;
        // A dependency already present in mods must not be installed a second time.
        let common = installed_common_mods(&instance);
        artifacts.retain(|file| {
            file.project == project || !COMMON_MODS.iter().any(|(id, modrinth, curseforge)| {
                let project_id = if provider == "modrinth" { modrinth } else { curseforge };
                file.project == *project_id && common.iter().any(|entry| entry.0 == *id)
            })
        });
        if artifacts.iter().map(|file| file.size).sum::<u64>() > 512 * 1024 * 1024 {
            return Err("Слишком большой набор зависимостей".into());
        }
        fs::create_dir_all(&mods).map_err(io_error)?;
        fs::create_dir_all(&receipts).map_err(io_error)?;
        let stage = tempfile::tempdir_in(&instance).map_err(io_error)?;
        let mut files = Vec::new();
        let mut names = HashSet::new();
        let artifact_count = artifacts.len();
        for (index, artifact) in artifacts.iter_mut().enumerate() {
            emit_progress(
                &app,
                &provider,
                &project,
                &game_version,
                &format!("Загрузка {}", artifact.name),
                (index * 90 / artifact_count) as u32,
                0,
            );
            if !mods.join(&artifact.name).exists() {
                for entry in fs::read_dir(&mods).map_err(io_error)? {
                    let entry = entry.map_err(io_error)?;
                    let name = entry.file_name().to_string_lossy().into_owned();
                    if safe_filename(&name).is_ok()
                        && entry.metadata().map_err(io_error)?.len() == artifact.size
                        && file_hash(&entry.path(), artifact.algorithm)?
                            .eq_ignore_ascii_case(&artifact.hash)
                    {
                        artifact.name = name;
                        break;
                    }
                }
            }
            if !names.insert(artifact.name.clone()) {
                return Err("Зависимости используют одинаковые имена файлов".into());
            }
            let destination = mods.join(&artifact.name);
            if destination.exists() {
                if fs::metadata(&destination).map_err(io_error)?.len() != artifact.size
                    || !file_hash(&destination, artifact.algorithm)?
                        .eq_ignore_ascii_case(&artifact.hash)
                {
                    return Err(format!(
                        "В папке mods уже есть другой файл {}",
                        artifact.name
                    ));
                }
            } else {
                download(
                    client,
                    artifact,
                    &stage.path().join(&artifact.name),
                    |bytes, speed| {
                        let percent = ((index as u64 * 90 + bytes * 90 / artifact.size)
                            / artifact_count as u64) as u32;
                        emit_progress(
                            &app,
                            &provider,
                            &project,
                            &game_version,
                            &format!("Загрузка {}", artifact.name),
                            percent,
                            speed,
                        );
                    },
                )?;
            }
            let source = if destination.exists() {
                destination
            } else {
                stage.path().join(&artifact.name)
            };
            files.push(InstalledFile {
                name: artifact.name.clone(),
                sha512: file_hash(&source, "sha512")?,
            });
        }
        let receipt = Receipt {
            projects: artifacts
                .iter()
                .map(|file| format!("{provider}:{}", file.project))
                .collect(),
            files,
        };
        let mut written = Vec::new();
        let commit = (|| -> Result<(), String> {
            emit_progress(
                &app,
                &provider,
                &project,
                &game_version,
                "Установка файлов",
                95,
                0,
            );
            for artifact in &artifacts {
                let source = stage.path().join(&artifact.name);
                if source.exists() {
                    let destination = mods.join(&artifact.name);
                    // Hard links publish a complete file and fail if another file already exists.
                    fs::hard_link(source, &destination).map_err(io_error)?;
                    written.push(destination);
                }
            }
            let mut record = tempfile::NamedTempFile::new_in(&receipts).map_err(io_error)?;
            record
                .write_all(
                    &serde_json::to_vec(&receipt)
                        .map_err(|_| "Не удалось сохранить список модов")?,
                )
                .map_err(io_error)?;
            record.as_file().sync_all().map_err(io_error)?;
            record
                .persist(&receipt_file)
                .map_err(|_| "Не удалось сохранить список модов")?;
            Ok(())
        })();
        if let Err(error) = commit {
            for file in written {
                let _ = fs::remove_file(file);
            }
            return Err(error);
        }
        emit_progress(
            &app,
            &provider,
            &project,
            &game_version,
            "Установлено",
            100,
            0,
        );
        Ok(Installation {
            count: artifacts.len(),
            directory: mods.to_string_lossy().into(),
        })
    })
    .await
    .map_err(|_| "Не удалось установить мод".to_string())?
}
