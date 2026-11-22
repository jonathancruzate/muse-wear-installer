#!/usr/bin/env bash
# Muse Wear Installer — one-time on-watch bootstrap.
#
# This is the LAST ADB install this app ever needs: it puts Muse
# Wear Installer itself on the watch over the PC lane (see
# BOOTSTRAP.md for the full steps and prerequisites). Every face
# install after this one goes phone -> watch over the Data Layer.
#
# Usage:
#   bootstrap.sh <watch-ip:port> [apk-path]
#
#   <watch-ip:port>  the CURRENT endpoint from the watch's
#                    Settings > Developer options > Wireless
#                    debugging screen ("IP address & Port"). The
#                    port rotates; it is never guessed.
#   [apk-path]       defaults to the project's built v1.0.0 APK.
#
# Lane (fleet-standard, skill local-pc-lane): PC over the tailnet
# SSH forwarder (127.0.0.1:2222), adb on the PC at
# C:\Users\jonat\devtools\android-sdk\platform-tools\adb.exe.
# The PC adb daemon is per-session: every remote call chains
# `adb connect` first.
set -euo pipefail

ENDPOINT="${1:?usage: bootstrap.sh <watch-ip:port> [apk-path]}"
APK="${2:-$HOME/workspace/muse-wear-installer/app/build/MuseWearInstaller-v1.0.0.apk}"
PKG="com.jonathancruzate.metamusewearinstaller"
ADB='C:\Users\jonat\devtools\android-sdk\platform-tools\adb.exe'
PC_APK='C:\Users\jonat\Downloads\MuseWearInstaller.apk'
SSH=(ssh -p 2222 -i "$HOME/.ssh/id_ed25519" jonat@127.0.0.1)
SCP=(scp -P 2222 -i "$HOME/.ssh/id_ed25519")

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
echo "== artifact =="
sha256sum "$APK"
stat -c '%s bytes' "$APK"

echo "== copy to PC =="
"${SCP[@]}" "$APK" "jonat@127.0.0.1:Downloads/MuseWearInstaller.apk"

echo "== connect + identity (expect SM-L505U) =="
"${SSH[@]}" "$ADB connect $ENDPOINT && $ADB -s $ENDPOINT shell getprop ro.product.model"

echo "== install the agent =="
"${SSH[@]}" "$ADB connect $ENDPOINT && $ADB -s $ENDPOINT install -r \"$PC_APK\""

echo "== grant install permission (appop, one-time) =="
# REQUEST_INSTALL_PACKAGES is an appop, not a runtime permission:
# `pm grant` does not apply. This sets the per-app "install unknown
# apps" allowance the PackageInstaller session checks.
"${SSH[@]}" "$ADB connect $ENDPOINT && $ADB -s $ENDPOINT shell cmd appops set --uid $PKG REQUEST_INSTALL_PACKAGES allow"

echo "== verify: appop + package record =="
"${SSH[@]}" "$ADB connect $ENDPOINT && $ADB -s $ENDPOINT shell cmd appops get $PKG REQUEST_INSTALL_PACKAGES"
"${SSH[@]}" "$ADB connect $ENDPOINT && $ADB -s $ENDPOINT shell dumpsys package $PKG | findstr /C:versionName /C:firstInstallTime /C:lastUpdateTime"

echo
echo "Bootstrap commands sent. Read back the outputs above:"
echo "  - install printed Success"
echo "  - appops get shows REQUEST_INSTALL_PACKAGES: allow"
echo "  - versionName=1.0.0 present in the package record"
echo "Then open 'Muse Wear Installer' in the watch's app drawer once"
echo "and allow its notification permission if asked — done. Face"
echo "installs from here on arrive as on-watch prompts."
