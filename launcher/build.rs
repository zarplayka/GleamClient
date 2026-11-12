fn main() {
    println!("cargo:rerun-if-changed=icons/icon.ico");
    if std::env::var_os("CARGO_FEATURE_INSTALLER").is_some() {
        build_installer();
        return;
    }
    println!("cargo:rerun-if-env-changed=GLEAM_CLIENT_JAR");
    let client_jar = std::env::var_os("GLEAM_CLIENT_JAR")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| "../build/launcher-client/gleamclient.jar".into());
    println!("cargo:rerun-if-changed={}", client_jar.display());
    let embedded_jar =
        std::path::PathBuf::from(std::env::var_os("OUT_DIR").unwrap()).join("gleamclient.jar");
    std::fs::copy(&client_jar, embedded_jar).unwrap_or_else(|error| {
        panic!(
            "Could not embed {}: {error}. Set GLEAM_CLIENT_JAR to the built client JAR.",
            client_jar.display()
        )
    });
    embed_curseforge_key();
    tauri_build::build();
}

fn embed_curseforge_key() {
    use std::{env, fs, io::ErrorKind, path::PathBuf};

    println!("cargo:rerun-if-env-changed=GLEAM_CURSEFORGE_KEY");
    println!("cargo:rerun-if-env-changed=APPDATA");
    let key = env::var("GLEAM_CURSEFORGE_KEY").ok().or_else(|| {
        let app_data = PathBuf::from(env::var_os("APPDATA")?);
        let configs = ["GleamClient", "xyz.gleamclient.launcher"]
            .map(|directory| app_data.join(directory).join("catalog-keys.json"));
        for config in configs {
            match fs::read(&config) {
                Ok(bytes) => {
                    println!("cargo:rerun-if-changed={}", config.display());
                    let json: serde_json::Value = serde_json::from_slice(&bytes)
                        .unwrap_or_else(|_| panic!("Invalid local catalog key configuration"));
                    if let Some(key) = json["curseforge"].as_str().filter(|key| !key.trim().is_empty()) {
                        return Some(key.to_owned());
                    }
                }
                Err(error) if error.kind() == ErrorKind::NotFound => {}
                Err(_) => panic!("Could not read local catalog key configuration"),
            }
        }
        None
    });
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap()).join("curseforge-key.txt");
    fs::write(output, key.as_deref().unwrap_or("").trim()).expect("stage CurseForge key");
}

fn build_installer() {
    use std::{env, fs, path::PathBuf};

    const PREVIEW: &str = "../website/tools/installer-preview.html";
    const LOGO: &str = "src/main/resources/assets/orion-client/textures/cosmetics/orion_badge.png";
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    let ui = PathBuf::from("../build/gleam-installer/ui");
    fs::create_dir_all(&ui).expect("create installer UI directory");
    for (source, name) in [
        ("../website/assets/onest-regular.ttf", "onest-regular.ttf"),
        ("../website/assets/onest-bold.ttf", "onest-bold.ttf"),
        ("icons/icon.ico", "icon.ico"),
        (&format!("../{LOGO}"), "gleam-eye.png"),
    ] {
        println!("cargo:rerun-if-changed={source}");
        fs::copy(source, ui.join(name)).expect("stage installer asset");
    }
    println!("cargo:rerun-if-changed={PREVIEW}");
    let html = fs::read_to_string(PREVIEW)
        .expect("read installer preview")
        .replace("../../launcher/icons/icon.ico", "icon.ico")
        .replace(&format!("../../{LOGO}"), "gleam-eye.png")
        .replace("../assets/onest-", "onest-");
    let (markup, script) = html.split_once("<script>").expect("installer script opening tag");
    let (script, end) = script.split_once("</script>").expect("installer script closing tag");
    fs::write(ui.join("installer.js"), script).expect("stage installer script");
    fs::write(
        ui.join("index.html"),
        format!(r#"{markup}<script src="installer.js"></script>{end}"#),
    )
    .expect("stage installer HTML");

    println!("cargo:rerun-if-env-changed=GLEAM_LAUNCHER_EXE");
    let launcher = env::var_os("GLEAM_LAUNCHER_EXE")
        .map(PathBuf::from)
        .unwrap_or_else(|| "../build/launcher-native/Gleam Launcher.exe".into());
    println!("cargo:rerun-if-changed={}", launcher.display());
    fs::copy(&launcher, output.join("gleam-launcher.exe"))
        .unwrap_or_else(|error| panic!("Could not embed {}: {error}", launcher.display()));

    let config = "installer/tauri.conf.json";
    tauri_build::try_build(tauri_build::Attributes::new().config_path(config))
        .expect("build installer Tauri context");
}
