#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]

use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::{Component, Path, PathBuf},
    sync::{Arc, Mutex},
};
use tauri::Emitter;

const LAUNCHER: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/gleam-launcher.exe"));
const EXE: &str = "Gleam Launcher.exe";
const SHORTCUT: &str = "Gleam Launcher.lnk";

#[derive(Clone, Default)]
struct Installer(Arc<Mutex<Job>>);

#[derive(Default, PartialEq, Eq)]
enum Job {
    #[default]
    Idle,
    Copying,
    Cancelled,
    Committing,
}

struct ActiveInstall(Installer);

impl Drop for ActiveInstall {
    fn drop(&mut self) {
        if let Ok(mut state) = self.0.0.lock() {
            *state = Job::Idle;
        }
    }
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct Installed {
    shortcut_failed: bool,
}

fn destination(path: &str) -> Result<PathBuf, &'static str> {
    const DEFAULT_PREFIX: &str = "%LOCALAPPDATA%\\";
    let path = path.trim();
    let path = if path.get(..DEFAULT_PREFIX.len()).is_some_and(|prefix| prefix.eq_ignore_ascii_case(DEFAULT_PREFIX)) {
        dirs::data_local_dir().ok_or("folder")?.join(&path[DEFAULT_PREFIX.len()..])
    } else {
        PathBuf::from(path)
    };
    if !path.is_absolute()
        || !path.components().any(|part| matches!(part, Component::Normal(_)))
        || path.components().any(|part| matches!(part, Component::ParentDir))
    {
        return Err("folder");
    }
    Ok(path)
}

fn install_files(
    directory: &Path,
    desktop: Option<&Path>,
    installer: &Installer,
    report: impl Fn(u8),
) -> Result<Installed, &'static str> {
    fs::create_dir_all(directory).map_err(|_| "folder")?;
    let executable = directory.join(EXE);
    let mut staged = tempfile::NamedTempFile::new_in(directory).map_err(|_| "write")?;
    let mut written = 0;
    report(10);
    for chunk in LAUNCHER.chunks(512 * 1024) {
        if *installer.0.lock().map_err(|_| "install")? == Job::Cancelled {
            return Err("cancelled");
        }
        staged.write_all(chunk).map_err(|_| "write")?;
        written += chunk.len();
        report((10 + written * 74 / LAUNCHER.len()) as u8);
    }
    staged.as_file().sync_all().map_err(|_| "write")?;

    {
        let mut state = installer.0.lock().map_err(|_| "install")?;
        if *state == Job::Cancelled {
            return Err("cancelled");
        }
        *state = Job::Committing;
    }
    report(88);
    let lock = OpenOptions::new().read(true).write(true).create(true).truncate(false)
        .open(directory.join(".gleam-update.lock")).map_err(|_| "write")?;
    lock.try_lock().map_err(|_| "busy")?;
    staged.persist(&executable).map_err(|error| match error.error.raw_os_error() {
        Some(5 | 32) if executable.exists() => "replace",
        _ => "write",
    })?;
    let shortcut_failed = desktop.is_some_and(|path| create_shortcut(path, &executable).is_err());
    report(100);
    Ok(Installed { shortcut_failed })
}

fn create_shortcut(desktop: &Path, executable: &Path) -> windows::core::Result<()> {
    use std::os::windows::ffi::OsStrExt;
    use windows::{
        core::{Interface, PCWSTR},
        Win32::{
            System::Com::{
                CoCreateInstance, CoInitializeEx, CoUninitialize, IPersistFile,
                CLSCTX_INPROC_SERVER, COINIT_APARTMENTTHREADED,
            },
            UI::Shell::{IShellLinkW, ShellLink},
        },
    };

    struct Com;
    impl Drop for Com {
        fn drop(&mut self) {
            unsafe { CoUninitialize() };
        }
    }
    let wide = |path: &Path| path.as_os_str().encode_wide().chain([0]).collect::<Vec<_>>();
    let temporary = tempfile::tempdir_in(desktop)?;
    let link_path = temporary.path().join(SHORTCUT);
    unsafe {
        CoInitializeEx(None, COINIT_APARTMENTTHREADED).ok()?;
        let _com = Com;
        let link: IShellLinkW = CoCreateInstance(&ShellLink, None, CLSCTX_INPROC_SERVER)?;
        link.SetPath(PCWSTR(wide(executable).as_ptr()))?;
        link.SetWorkingDirectory(PCWSTR(wide(executable.parent().unwrap()).as_ptr()))?;
        let file: IPersistFile = link.cast()?;
        file.Save(PCWSTR(wide(&link_path).as_ptr()), true)?;
    }
    fs::rename(link_path, desktop.join(SHORTCUT))?;
    Ok(())
}

#[tauri::command]
async fn install_launcher(
    app: tauri::AppHandle,
    state: tauri::State<'_, Installer>,
    path: String,
    desktop_shortcut: bool,
) -> Result<Installed, &'static str> {
    let directory = destination(&path)?;
    let installer = state.inner().clone();
    {
        let mut job = installer.0.lock().map_err(|_| "install")?;
        if *job != Job::Idle {
            return Err("busy");
        }
        *job = Job::Copying;
    }
    let active = ActiveInstall(installer.clone());
    tauri::async_runtime::spawn_blocking(move || {
        let _active = active;
        let desktop = desktop_shortcut.then(dirs::desktop_dir).flatten();
        let mut result = install_files(&directory, desktop.as_deref(), &installer, |progress| {
            let _ = app.emit("installation-progress", progress);
        })?;
        result.shortcut_failed |= desktop_shortcut && desktop.is_none();
        Ok(result)
    })
    .await
    .map_err(|_| "install")?
}

#[tauri::command]
fn cancel_install(state: tauri::State<'_, Installer>) -> bool {
    if let Ok(mut job) = state.0.lock() {
        if *job == Job::Copying {
            *job = Job::Cancelled;
            return true;
        }
    }
    false
}

#[tauri::command]
fn show_installer(window: tauri::WebviewWindow) -> Result<(), String> {
    window.show().map_err(|error| error.to_string())
}

fn main() {
    let installer = Installer::default();
    tauri::Builder::default()
        .manage(installer.clone())
        .invoke_handler(tauri::generate_handler![install_launcher, cancel_install, show_installer])
        .on_window_event(move |_, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                if installer.0.lock().is_ok_and(|job| *job != Job::Idle) {
                    api.prevent_close();
                }
            }
        })
        .run(tauri::generate_context!("installer/tauri.conf.json"))
        .expect("could not open Gleam Launcher Setup");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn installs_and_updates_launcher_with_a_shortcut() {
        let root = tempfile::tempdir().unwrap();
        let directory = root.path().join("Gleam Launcher");
        let desktop = root.path().join("Desktop");
        fs::create_dir(&desktop).unwrap();
        let installer = Installer::default();
        for _ in 0..2 {
            *installer.0.lock().unwrap() = Job::Copying;
            let result = install_files(&directory, Some(&desktop), &installer, |_| {}).unwrap();
            assert!(!result.shortcut_failed);
            assert_eq!(fs::read(directory.join(EXE)).unwrap(), LAUNCHER);
            assert!(desktop.join(SHORTCUT).is_file());
        }
    }

    #[test]
    fn cancelling_preserves_existing_launcher() {
        let root = tempfile::tempdir().unwrap();
        let exe = root.path().join(EXE);
        fs::write(&exe, b"previous").unwrap();
        let installer = Installer::default();
        *installer.0.lock().unwrap() = Job::Copying;
        let result = install_files(root.path(), None, &installer, |percent| {
            if percent >= 20 {
                *installer.0.lock().unwrap() = Job::Cancelled;
            }
        });
        assert!(matches!(result, Err("cancelled")));
        assert_eq!(fs::read(exe).unwrap(), b"previous");
        assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1);
    }
}
