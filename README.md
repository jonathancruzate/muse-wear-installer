# Muse Wear Installer

A Wear OS companion app that receives watch-face APKs from **Muse
Device Assistance** on the paired phone over the Wearable Data Layer
and installs them after one on-watch tap — no PC, no ADB, no Wi-Fi
debugging port. Built 2026-11-11 to replace the PC+ADB deploy lane,
whose rotating debugging port kept breaking face installs.

- **Package:** `com.jonathancruzate.metamusewearinstaller`
- **Version:** 1.0.0 (versionCode 1), minSdk 30, targetSdk 35
- **APK:** `app/build/MuseWearInstaller-v1.0.0.apk` — 414,965 bytes,
  SHA-256 `1caec298ecc99e72af4fe777409d57acb97ecfe77f222359fe06af0008acc808`
- **Signer:** project keystore (`app/keystore/`, auto-created by
  `app/build.sh`), cert SHA-256
  `07dca87043c69917ab8be25202744698f4fa777e27840a674541cbd5ce5705e8`
- **Phone half:** Muse Device Assistance v1.13.0+ (the
  `wear_install_face` command) — `~/workspace/muse-device-assistance/`

## How it works (30 seconds)

Muse enqueues a `wear_install_face` command {label, https-url,
sha256} on the fleet command channel → the phone app downloads the
APK, verifies its hash, and relays it to this app over the Data
Layer (offer by MessageClient, bytes by ChannelClient) → this app
re-verifies the hash, raises a prompt with the label and
Install/Dismiss → on Install, a PackageInstaller session runs the
install (Android shows its own confirmation) → every stage is
reported back to the phone and into the command result Muse reads.
Full design: [DESIGN.md](DESIGN.md). First-time watch setup:
[bootstrap/BOOTSTRAP.md](bootstrap/BOOTSTRAP.md).

## Build

```bash
app/build.sh        # raw aapt2/javac/d8 toolchain, fleet pattern
```

JVM protocol tests:

```bash
JDK=~/workspace/custom-skills/local-rea/tools/jdk
$JDK/bin/javac -cp app/src -d /tmp/pt tools/ProtocolTest.java \
  app/src/com/jonathancruzate/metamusewearinstaller/Protocol.java
$JDK/bin/java -cp /tmp/pt ProtocolTest
```

## Layout

- `app/` — the watch app (framework-only Android; vendored GMS jars
  in `app/libs/`, their source AARs/POMs in `app/libs-src/`)
- `bootstrap/` — the one-time ADB install of the agent itself
- `tools/ProtocolTest.java` — the wire-contract unit tests
- `docs/` — build/version notes
- `SKILL.md` — how Muse uses it (mirrored to
  `~/workspace/custom-skills/local-muse-wear-installer/`)

## v1.0.1 (2026-11-21) — unique Muse app icon

Icon-only release (versionCode 2). Launcher icon is now this app's own
from the approved unique Muse app icon set (`~/workspace/muse-app-logos/`):
original Muse blue M on a light tile plus a blue watch badge, superseding
the 2026-11-01 all-same-icon rule. Same icon as the phone companion app.
Legacy mipmaps + round + adaptive icon (background #F0F2F5, foreground at
76% centred). Verified: icon extracted from the built APK matches the
approved art; build guards + apksigner passed. APK:
app/build/MuseWearInstaller-v1.0.1.apk, SHA-256 `0767fc76231f26e544a39fbf79c64a2d879ba21204df13d20bd594befdd872b1`. Not installed by
this handling; ready to install.
