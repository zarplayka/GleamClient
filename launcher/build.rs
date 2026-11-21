fn main() {
    println!("cargo:rerun-if-changed=icons/icon.ico");
    if std::env::var_os("CARGO_FEATURE_INSTALLER").is_some() {
        build_installer();
        return;
    }
    embed_client_jars();
    embed_curseforge_key();
    embed_minecraft_profiler();
    export_cosmetic_preview();
    tauri_build::build();
}

fn java_tool(name: &str) -> std::path::PathBuf {
    use std::{env, path::PathBuf};
    env::var_os("JAVA_HOME")
        .map(|home| PathBuf::from(home).join("bin").join(format!("{name}{}", env::consts::EXE_SUFFIX)))
        .unwrap_or_else(|| PathBuf::from(name))
}

fn embed_minecraft_profiler() {
    use std::{env, fs, path::PathBuf, process::Command};

    const SOURCE: &str = "MinecraftProfiler.java";
    println!("cargo:rerun-if-changed={SOURCE}");
    println!("cargo:rerun-if-env-changed=JAVA_HOME");
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    let classes = tempfile::tempdir_in(&output).expect("stage Minecraft profiler classes");
    let compiled = Command::new(java_tool("javac"))
        .args(["--release", "21", "-encoding", "UTF-8", "-d"])
        .arg(classes.path()).arg(SOURCE)
        .status().expect("compile Minecraft profiler");
    assert!(compiled.success(), "compile Minecraft profiler");

    let manifest = output.join("minecraft-profiler.mf");
    fs::write(&manifest, "Premain-Class: gleam.diagnostics.MinecraftProfiler\r\n\r\n")
        .expect("write Minecraft profiler manifest");
    let archived = Command::new(java_tool("jar"))
        .args(["--create", "--file"]).arg(output.join("minecraft-profiler.jar"))
        .arg("--manifest").arg(manifest).arg("-C").arg(classes.path()).arg(".")
        .status().expect("package Minecraft profiler");
    assert!(archived.success(), "package Minecraft profiler");
}

fn export_cosmetic_preview() {
    use std::{env, fs, path::PathBuf, process::Command};

    let sources = [
        "ExportCosmeticPreview.java",
        "../src/main/java/ru/orion/client/effect/OrionWingMesh.java",
        "../src/main/java/ru/orion/client/effect/OrionEmoteClips.java",
    ];
    for source in sources {
        println!("cargo:rerun-if-changed={source}");
    }
    println!("cargo:rerun-if-env-changed=JAVA_HOME");
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    let classes = output.join("cosmetic-preview-classes");
    fs::create_dir_all(&classes).expect("create cosmetic preview compiler output");
    let compiled = Command::new(java_tool("javac"))
        .args(["-encoding", "UTF-8", "-d"]).arg(&classes).args(sources)
        .status().expect("compile cosmetic preview exporter with the project JDK");
    assert!(compiled.success(), "compile cosmetic preview exporter");

    // Write and publish only a complete asset. The same path is included by Tauri.
    let target = PathBuf::from("../build/launcher-cosmetics.json");
    fs::create_dir_all(target.parent().unwrap()).expect("create cosmetic preview asset directory");
    let staged = tempfile::NamedTempFile::new_in(target.parent().unwrap()).expect("stage cosmetic preview asset");
    let exported = Command::new(java_tool("java"))
        .arg("-cp").arg(&classes).arg("ExportCosmeticPreview").arg(staged.path())
        .status().expect("run cosmetic preview exporter");
    assert!(exported.success(), "export cosmetic preview geometry");
    staged.persist(target).expect("publish cosmetic preview geometry locally");
}

fn embed_client_jars() {
    use std::{env, fs, path::PathBuf};

    const MANIFEST: &str = "../build/supported-versions.json";
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    let mut manifest: Option<Vec<serde_json::Value>> = None;
    for (version, override_var) in [
        ("1.21.11", "GLEAM_CLIENT_JAR_1_21_11"),
        ("26.1.2", "GLEAM_CLIENT_JAR_26_1_2"),
        ("26.2", "GLEAM_CLIENT_JAR_26_2"),
        ("26.3", "GLEAM_CLIENT_JAR"),
    ] {
        println!("cargo:rerun-if-env-changed={override_var}");
        let jar = env::var_os(override_var).map(PathBuf::from).unwrap_or_else(|| {
            let targets = manifest.get_or_insert_with(|| {
                println!("cargo:rerun-if-changed={MANIFEST}");
                let bytes = fs::read(MANIFEST).expect("Run gradlew.bat build to build the client JARs");
                serde_json::from_slice(&bytes).expect("Read the supported Minecraft versions")
            });
            let name = targets.iter()
                .find(|target| target["minecraft"] == version)
                .and_then(|target| target["jar"].as_str())
                .unwrap_or_else(|| panic!("No client JAR for Minecraft {version} in {MANIFEST}"));
            PathBuf::from("../build/libs").join(name)
        });
        println!("cargo:rerun-if-changed={}", jar.display());
        fs::copy(&jar, output.join(format!("gleamclient-mc{version}.jar"))).unwrap_or_else(|error| {
            panic!("Could not embed Minecraft {version} client {}: {error}", jar.display())
        });
    }
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
