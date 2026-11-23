#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]

use tauri::{LogicalSize, Manager, PhysicalPosition};

mod auth;
mod catalog;
mod discord;
mod game;
mod minecraft;
mod profile_content;
mod profile_import;
mod settings_sync;
mod updater;

#[cfg(windows)]
fn configure_storage(context: &mut tauri::Context<tauri::Wry>) {
    use tauri::utils::config::{AppDirectoriesOverride, AppDirectoryOverrides};

    let (Some(roaming), Some(local)) = (dirs::data_dir(), dirs::data_local_dir()) else {
        return;
    };
    let legacy_name = &context.config().identifier;
    let legacy_data = roaming.join(legacy_name);
    let legacy_local = local.join(legacy_name);
    let instances = legacy_data.join("instances");
    if game::supported_versions().map(|version| instances.join(version))
        .chain(profile_import::stored_profiles(&instances).map(|(_, path)| path))
        .any(|path| game::check_storage_idle(&path).is_err()) {
        return;
    }
    let data = migrate_storage(&legacy_data, roaming.join("GleamClient"));
    if data == legacy_data {
        return;
    }
    let local_data = migrate_storage(&legacy_local, local.join("GleamClient"));
    context.config_mut().app.app_directories_override =
        Some(AppDirectoriesOverride::Directories(AppDirectoryOverrides {
            config: Some(data.clone()),
            data: Some(data),
            local_data: Some(local_data.clone()),
            cache: Some(local_data),
            ..Default::default()
        }));
}

#[cfg(windows)]
fn migrate_storage(legacy: &std::path::Path, destination: std::path::PathBuf) -> std::path::PathBuf {
    use std::{fs, io::ErrorKind};

    match fs::symlink_metadata(legacy) {
        Err(error) if error.kind() == ErrorKind::NotFound => destination,
        Ok(_) if !destination.exists() && fs::rename(legacy, &destination).is_ok() => destination,
        Ok(_) if destination.is_dir() && matches!(legacy.try_exists(), Ok(false)) => destination,
        _ => legacy.to_owned(),
    }
}

#[tauri::command]
fn open_project_link(link: &str, presence: tauri::State<'_, discord::Presence>) -> Result<(), String> {
    let url = match link {
        "site" => "https://gleamclient.xyz/",
        "discord" => "https://discord.gg/JMjFrMWsku",
        "telegram" => "https://t.me/OrionClientick",
        "github" => "https://github.com/zarplayka/GleamClient",
        "issues" => "https://github.com/zarplayka/GleamClient/issues",
        "register" => "https://gleamclient.xyz/auth.html",
        "plus" => "https://gleamclient.xyz/#plus",
        _ => return Err("Неизвестная ссылка".into()),
    };
    open_browser(url)?;
    if link == "site" || link == "plus" {
        presence.site_opened();
    }
    Ok(())
}

#[cfg(windows)]
fn open_browser(url: &str) -> Result<(), String> {
    use std::{ffi::c_void, ptr::null};

    #[link(name = "shell32")]
    extern "system" {
        fn ShellExecuteW(
            window: *mut c_void,
            operation: *const u16,
            file: *const u16,
            parameters: *const u16,
            directory: *const u16,
            show: i32,
        ) -> isize;
    }

    #[link(name = "ole32")]
    extern "system" {
        fn CoInitializeEx(reserved: *mut c_void, flags: u32) -> i32;
        fn CoUninitialize();
    }

    let operation: Vec<u16> = "open".encode_utf16().chain([0]).collect();
    let url: Vec<u16> = url.encode_utf16().chain([0]).collect();
    // The auth command also calls this from a worker thread.
    let initialized = unsafe { CoInitializeEx(std::ptr::null_mut(), 2 | 4) } >= 0;
    let result = unsafe {
        ShellExecuteW(
            std::ptr::null_mut(),
            operation.as_ptr(),
            url.as_ptr(),
            null(),
            null(),
            1,
        )
    };
    if initialized {
        unsafe { CoUninitialize() };
    }
    if result > 32 {
        Ok(())
    } else {
        Err("browser_open".into())
    }
}

#[cfg(not(windows))]
fn open_browser(url: &str) -> Result<(), String> {
    let browser = if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(browser)
        .arg(url)
        .spawn()
        .map(|_| ())
        .map_err(|_| "browser_open".into())
}

fn main() {
    if std::env::args_os().nth(1).is_some_and(|arg| arg == "--minecraft-account-worker") {
        if minecraft::account_worker().is_err() {
            std::process::exit(1);
        }
        return;
    }
    let mut context = tauri::generate_context!();
    #[cfg(windows)]
    configure_storage(&mut context);
    tauri::Builder::default()
        .manage(catalog::Catalog::new())
        .manage(discord::Presence::default())
        .manage(auth::Auth::new())
        .manage(minecraft::Minecraft::default())
        .manage(game::Game::default())
        .manage(updater::Updater::default())
        .invoke_handler(tauri::generate_handler![
            catalog::catalog_search,
            catalog::catalog_install,
            catalog::repair_zakoopt,
            catalog::instance_storage,
            catalog::open_instance_folder,
            open_project_link,
            discord::discord_presence_selection,
            auth::gleam_auth,
            auth::cosmetic_loadout,
            auth::gleam_friends,
            minecraft::minecraft_account,
            game::launch_minecraft,
            game::minecraft_running,
            game::minecraft_activity,
            game::minecraft_resources,
            profile_content::profile_content,
            profile_content::profile_server_status,
            profile_content::profile_toggle_mod,
            profile_content::open_profile_content,
            profile_content::open_crash_report,
            profile_import::discover_profiles,
            profile_import::imported_profiles,
            profile_import::create_profile,
            profile_import::import_profiles,
            profile_import::dismiss_profile_import,
            game::memory_limits,
            updater::check_launcher_update,
            updater::install_launcher_update,
            updater::restart_launcher_update,
            updater::cancel_launcher_update,
            game::stop_minecraft,
            game::cancel_minecraft_install
        ])
        .on_window_event(|window, event| {
            if matches!(event, tauri::WindowEvent::Focused(true)) {
                window.state::<discord::Presence>().focused();
            }
        })
        .setup(|app| {
            app.state::<discord::Presence>().start();
            let window = app
                .get_webview_window("main")
                .ok_or("main launcher window is missing")?;
            if let Some(monitor) = window.current_monitor()? {
                let work_area = monitor.work_area();
                let scale = monitor.scale_factor();
                let available_width = work_area.size.width as f64 / scale;
                let available_height = work_area.size.height as f64 / scale;
                let fit = (available_width * 0.92 / 1360.0)
                    .min(available_height * 0.92 / 800.0)
                    .min(1.0);
                let width = 1360.0 * fit;
                let height = 800.0 * fit;
                window.set_min_size(Some(LogicalSize::new(
                    690.0_f64.min(width),
                    560.0_f64.min(height),
                )))?;
                window.set_size(LogicalSize::new(width, height))?;
                window.set_position(PhysicalPosition::new(
                    work_area.position.x
                        + ((work_area.size.width as f64 - width * scale) / 2.0).round() as i32,
                    work_area.position.y
                        + ((work_area.size.height as f64 - height * scale) / 2.0).round() as i32,
                ))?;
            }
            window.show()?;
            Ok(())
        })
        .build(context)
        .expect("failed to build Gleam Launcher")
        .run(|app, event| {
            if matches!(event, tauri::RunEvent::Exit) {
                app.state::<discord::Presence>().stop();
            }
            if let tauri::RunEvent::ExitRequested { code: None, api, .. } = event {
                if app.state::<game::Game>().is_syncing_settings() {
                    api.prevent_exit();
                }
            }
        });
}

#[cfg(all(test, windows))]
mod tests {
    use super::migrate_storage;
    use std::fs;

    #[test]
    fn moves_existing_storage_and_reuses_the_new_root() {
        let root = tempfile::tempdir().unwrap();
        let legacy = root.path().join("xyz.gleamclient.launcher");
        let destination = root.path().join("GleamClient");
        fs::create_dir_all(legacy.join("instances/26.3/saves")).unwrap();
        fs::write(legacy.join("minecraft-accounts.dat"), b"test account").unwrap();
        fs::write(legacy.join("instances/26.3/saves/test"), b"test world").unwrap();
        let game_dir = legacy.join("instances/26.3");
        let lock = fs::File::create(game_dir.join(".gleam-launch.lock")).unwrap();
        lock.try_lock().unwrap();
        assert!(super::game::check_storage_idle(&game_dir).is_err());
        drop(lock);
        super::game::check_storage_idle(&game_dir).unwrap();
        assert_eq!(migrate_storage(&legacy, destination.clone()), destination);
        assert!(!legacy.exists());
        assert_eq!(fs::read(destination.join("minecraft-accounts.dat")).unwrap(), b"test account");
        assert_eq!(fs::read(destination.join("instances/26.3/saves/test")).unwrap(), b"test world");
        assert_eq!(migrate_storage(&legacy, destination.clone()), destination);
    }

    #[test]
    fn keeps_legacy_storage_if_the_target_already_exists() {
        let root = tempfile::tempdir().unwrap();
        let legacy = root.path().join("xyz.gleamclient.launcher");
        let destination = root.path().join("GleamClient");
        fs::create_dir(&legacy).unwrap();
        fs::create_dir(&destination).unwrap();
        assert_eq!(migrate_storage(&legacy, destination.clone()), legacy);
        assert!(legacy.is_dir() && destination.is_dir());
    }
}
