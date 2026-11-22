# Muse Wear Installer — how it works, setup, and how Muse uses it

Muse Wear Installer is the Wear OS half of the fleet's watch-face
install lane: it lets Muse put a watch-face APK on Jonathan's
Galaxy Watch 8 Classic with **one tap on the watch** — no PC, no
ADB, no Wi-Fi debugging port. It exists because the old deploy lane
(PC awake + adb + a rotating debugging port) failed twice in one
night (2026-11-10/11). Project home: `~/workspace/muse-wear-installer/`
(watch app); phone half: the standalone **Muse Wear Installer**
(phone app) in `~/workspace/muse-wear-installer-phone/` (split out
of Muse Device Assistance v1.13 on 2026-11-11 at Jonathan's
order — "Make this a separate phone apk").

## How it works

1. Muse enqueues a **`wear_install_face`** command on the Worker
   command channel **for device `wear-installer`** — the
   wearbridge lane, separate from Device Assistance's phonebridge
   lane: args `{label, https-url, sha256}`.
2. The phone app (Muse Wear Installer, phone app) gates it in
   `WearGate` (the `ShellAllowlist.checkWearInstall` rules,
   ported), downloads the APK from the URL, and verifies the
   SHA-256.
3. The phone relays the job to the watch over the **Wearable Data
   Layer**: an offer message (`/muse-wear-installer/offer`) and the
   APK bytes on a channel (`/muse-wear-installer/apk/<job_id>`).
4. The watch app **re-verifies the hash**, then raises a prompt:
   the label (e.g. "Swiss Army v6.2"), **Install / Dismiss**.
5. On Install, the watch app runs a **PackageInstaller session**;
   Android shows its own system install confirmation (a second,
   platform-owned tap). The verdict — `installed`, `failed`,
   `verify_failed`, or `dismissed` — travels back over the Data
   Layer → phone → command result, so Muse reads the verified
   outcome in chat. Every stage is also mirrored in the
   wearbridge report's `wear_install` object and the watch app's
   idle screen.

Key properties: one job at a time; hash-verified on both devices
before anything is offered for install; a phone-side wait budget of
300 s — a timeout means "not tapped yet", never an auto-retry.

## Setup (one-time)

1. **Phone:** **Muse Wear Installer v1.0.1** (phone app) installed
   (`~/workspace/muse-wear-installer-phone/app/MuseWearInstallerPhone-v1.0.1.apk`),
   opened once to enter the Worker base URL + secret and tap
   **Save & start lane**, and excluded from battery optimization.
   As configured 2026-11-13 the app holds the LEGACY pair —
   base `https://jonathancruzate.com/mode-sync` + the legacy shared
   secret. **v1.0.2 (built + installed 2026-11-16) adds the
   transport-directive handler** (probe-before-switch, 24 h
   probation revert; decisions in TransportDirective, suite 22/22),
   so the migration is now server-driven: arm the Worker's KV flag
   `wearbridge:transport-directive` and the app switches itself to
   `/bridge` + the wear lane secret on its next poll. STATUS
   2026-11-16 late: v1.0.2 is installed (vc3, read-back verified)
   but the lane service did not survive the update, so the switch
   has NOT happened yet — it completes once the lane is restarted
   (tap **Save & start lane**; fields are pre-filled) and the flag
   is re-armed; wear legacy retirement stays gated on the verified
   switch (full record: the phone project's
   docs/v1.0.2-build-2026-11-16.md). Until that switch verifies,
   the wear lane stays dual-accept for it (BRIDGE-MIGRATION-
   2026-11-13.md). **INTERLOCK: never change only one field on
   that setup screen.** A future on-device migration must change
   BOTH fields in the same edit — base → `https://jonathancruzate.com/bridge`
   AND secret → the wear lane secret. Changing the base alone
   leaves the app presenting the legacy secret at /bridge, which
   works only while wear legacy acceptance remains; changing the
   secret alone at the old base works the same way. Either
   single-field change silently depends on the dual-accept that
   retirement will eventually close. (History: the verb
   first shipped inside Muse Device Assistance v1.13.0; that
   build is withdrawn as the lane's owner, and DA v1.14.0 —
   built on the v1.13 tree for `voice_bridge` — still carries
   the verb in its binary. Neither is the lane anymore: never
   enqueue wear commands on the phonebridge lane.)
2. **Watch:** run the bootstrap once —
   `~/workspace/muse-wear-installer/bootstrap/bootstrap.sh <watch-ip:port>`
   with the current port read from the watch's Wireless debugging
   screen; it installs the agent APK over ADB and grants the
   `REQUEST_INSTALL_PACKAGES` appop. Full steps:
   `bootstrap/BOOTSTRAP.md` in the project.
3. Open the watch app once and allow its notification permission.

## How Muse uses it

Stage the face APK at an HTTPS URL first (Muse's built-in storage:
`/opt/hatch/bin/remote-storage upload-file --path <face.apk>` —
note the link expires; enqueue promptly), compute its SHA-256,
then:

```bash
SHA=$(sha256sum face.apk | cut -d' ' -f1)
# Enqueue DIRECTLY against the Worker API at the /bridge base with
# the WEAR lane secret (bridge-secret-wear.txt in the mode-sync
# tree's hidden_files). The old phone-bridge-cmd.sh form is dead:
# the script refuses MODE_SYNC_BASE (retired 2026-11-11) and the
# box device-relay has no wear port. This direct form is how the
# 2026-11-15 rehearsal actually enqueued. The phone app itself
# still polls the Worker at its on-device /mode-sync base (KV is
# path-independent; same queue either way).
WEAR_SECRET=$(cat ~/workspace/cloudflare-workers/mode-sync/hidden_files/bridge-secret-wear.txt)
curl -s -X POST "https://jonathancruzate.com/bridge/phone-bridge/enqueue?secret=$WEAR_SECRET" \
  -H 'Content-Type: application/json' \
  -d "{\"device\":\"wear-installer\",\"template\":\"wear_install_face\",\"ttl_ms\":1800000,\"args\":[\"Swiss-Army-v6.2\",\"https://…/face.apk\",\"$SHA\"]}"
# → prints the command id; then read it back:
curl -s "https://jonathancruzate.com/bridge/phone-bridge/command-status?id=<id>&secret=$WEAR_SECRET"
```

Rules:

- **Label:** file-safe token, no spaces (`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)
  — use forms like `Swiss-Army-v6.2`. It is what Jonathan reads on
  the watch prompt.
- **URL:** https only, ≤2048 chars, no userinfo. The phone rejects
  anything else at the gate.
- **One command at a time:** enqueue singly (the channel executes
  one command per fetch; the watch refuses a second job while one
  is mid-flight).
- **Read the result, don't assume:** success is `ok=true` with
  `stage=installed` in the result tail. `watch_install_timeout`
  means the prompt is still waiting on the watch — tell Jonathan;
  do not re-enqueue (a duplicate would be refused `busy` or queue
  behind).
- **Activation is separate:** installing a face never selects it.
  Jonathan (or a later on-watch step) picks it in the Wear OS face
  picker — same as the ADB lane.
- **Face updates install in place** when signed with the same key
  as the installed face (the WFS debug keystore for WFS builds) —
  the installer is agnostic to the payload's own signing.

## Troubleshooting

| Symptom | Meaning / next step |
|---|---|
| `sha256_mismatch` (phone) | The hosted file changed or the wrong hash was enqueued. Recompute and re-enqueue. |
| `watch_not_connected` | No Wear node connected — watch off, out of range, or Wear OS app not paired. Check the watch is on and nearby. |
| `verify_failed` (watch) | Bytes arrived corrupted or truncated; the watch deleted them. Re-enqueue (fresh job). |
| `install_permission_missing` | The bootstrap appop grant is missing — re-run the bootstrap's appops step. |
| `watch_install_timeout` | Jonathan hasn't tapped yet (or dismissed-by-timeout confusion — check the wearbridge report's `wear_install.stage`, read back with `?device=wear-installer`). |
| Prompt never appears | Watch app installed? Notifications allowed? The watch idle screen shows the last job's stage. |

## Known limits (v1)

- **One watch.** Dad's Watch 8 Classic is the known next step
  (per-watch bootstrap + node selection by watch identity).
- The system's own install confirmation still appears after the
  app's prompt — two taps total, one of them Android's.
- Nothing about the on-watch path was emulator-verified (no working
  Wear emulator in this environment); the first bootstrap +
  rehearsal command is the live proof, read back stage by stage.
