# Bootstrap — the one last ADB install

Muse Wear Installer cannot install itself (an app that isn't on the
watch yet can't receive Data Layer jobs). This bootstrap puts the
agent on the watch once, over the existing PC + ADB lane. After it,
watch-face installs never touch ADB again.

**Status: RUN — 2026-11-13 01:39 EST, all read-backs green.** The
endpoint was *discovered*, not read off the watch: `adb mdns services`
and a raw multicast browse both came up empty (the documented norm on
this network), but a **unicast mDNS query from the PC to the watch's
IP (192.168.1.182:5353)** returned the live `_adb-tls-connect`
advertisement — port **42073**, TXT `name=SM-L505U` (helper scripts on
the PC: `Downloads\mdns_browse.py` / `mdns_unicast.py`). Run record:

- `bootstrap.sh 192.168.1.182:42073` → identity **SM-L505U** asserted,
  `adb install -r` → **Success**, appop read-back `Uid mode:
  REQUEST_INSTALL_PACKAGES: allow`, package record `versionName=1.0.0`,
  `firstInstallTime = lastUpdateTime = 2026-11-13 01:39:15`.
- Phone half the same session: **Muse Wear Installer (phone app)
  v1.0.1** installed on the S24+ (SM-S926U1) over its ADB lane
  (first installed 01:22 by a parallel handling; this session's
  `install -r` read back `lastUpdateTime=2026-11-13 01:40:20`,
  versionCode=2).
- **Still owed before the first rehearsal (Jonathan, on-device):**
  open the phone app once — enter the Worker base URL + secret, tap
  **Save & start lane**, exclude it from battery optimization (the
  app was verified NOT running / never configured); open the watch
  app once and allow its notification permission. The Swiss Army
  v6.2 delivery that night went via the ADB fallback instead —
  record: `~/workspace/wfs-fix/swiss-v6-2/DEPLOY.md`, fourth attempt.

## Rehearsal #1 — 2026-11-15 23:42 EST: FAILED at the watch hop (`watch_not_connected`)

Jonathan's one-time phone setup had landed by this run: the
wearbridge report was FRESH at run start (heartbeat
`received_at 2026-11-16T04:38:23Z`, plan "wear install lane idle"),
so the lane was live and polling. Ground truth on the Worker
migration, established the same run: the Worker is dual-served in
code (`/mode-sync*` + `/bridge*`), per-lane secrets
(`BRIDGE_SECRET_WEAR` etc.) are LIVE and authenticated this run's
reads, legacy secrets remain accepted for every lane (retirement
has NOT happened) — but the `/bridge` zone route does **not**
actually serve the Worker (a GET to `/bridge/phone-bridge/report`
returned the jonathancruzate.com website HTML), so the operative
base remains `https://jonathancruzate.com/mode-sync`. Note:
`phone-bridge-cmd.sh` currently refuses `MODE_SYNC_BASE` (retired
2026-11-11) and the box relay was never ported with the wear lane,
so this rehearsal enqueued **directly against the Worker API**
(`POST /mode-sync/phone-bridge/enqueue`, wear-lane secret) — the
skill's CLI example needs updating to match.

- Payload: Swiss Army v6.2 re-delivery — `SwissArmy.apk`
  (591,032 B, SHA-256
  `af4aebfbb29e4ac6afd5105315094839108071196ff2fd16eb7ef8c8169f5ab8`),
  staged via Muse remote storage (link expires 2026-11-18).
- Command: `cmd-9e1a9484-0240-4b3d-bbd7-7b421c6b516e`
  (`wear_install_face`, label `Swiss-Army-v6.2`, TTL 30 min),
  enqueued 04:42:39Z.
- Progression: `pending → delivered 04:42:54Z → done 04:42:55Z` —
  the phone fetched within ~15 s and executed immediately.
- **Terminal verdict: `ok=false`, stage `watch_not_connected`** —
  "no Wear OS node is connected to the phone" (execution 483 ms;
  mirrored in the wearbridge report's `wear_install` object at
  04:42:56Z). The job died at the phone→watch Data Layer hop,
  before download/relay: at execution time the phone saw **no
  connected Wear node at all**. The Worker → phone → gate →
  execute path is proven live; the watch link was not up (watch
  off, out of Bluetooth range, or its connection to the phone
  down — the evidence distinguishes "no node connected" from a
  watch-app failure, and cannot narrow further from here).
- Next rehearsal precondition: watch on, worn/near the phone,
  Wear connection up — then re-enqueue a fresh job (this command
  is terminal; nothing is queued).

## Rehearsal #2 — 2026-11-16 23:41 EST: watch reached, verdict timed out (`watch_install_timeout`)

Jonathan's "proceed" (23:36 EST) fired the retry. Lane liveness
re-verified first: wearbridge report heartbeats at
2026-11-17T04:35:40Z and 04:40:42Z (the app posts on a ≈5-minute
cadence; the second read was 27 s old at enqueue). The phone app
still polls at its on-device `/mode-sync` base (dual-accept),
unchanged.

- Payload re-staged: attempt #1's muse.ai link was dead early
  (HEAD → 403, GET → connection failure) despite its recorded
  2026-11-18 expiry, so the identical file was re-uploaded —
  `SwissArmy.apk`, 591,032 B, SHA-256
  `af4aebfbb29e4ac6afd5105315094839108071196ff2fd16eb7ef8c8169f5ab8`
  re-verified on disk; new link expires 2026-11-19T04:40:54Z.
- Command: `cmd-cf403374-4969-471e-b1bd-73c43cbbfd2f`
  (`wear_install_face`, label `Swiss-Army-v6.2`, TTL 30 min),
  enqueued 04:41:35Z directly against the Worker API at the
  `/bridge` base with the wear lane secret (the skill's corrected
  form).
- Progression: delivered to the phone 04:41:42Z (~7 s) → watch
  job `a4422c7e-91ba-4ae8-b5f7-880f30b21f33`: APK transferred to
  the watch by 04:41:45Z (wearbridge report stage `transferred`,
  "awaiting on-watch verdict") → no on-watch verdict inside the
  phone's 300 s budget → command terminal 04:46:43Z.
- **Terminal verdict: `ok=false`, `watch_install_timeout`** —
  "last stage transferring after 300s" (execution 300,007 ms).
  Unlike attempt #1, the watch node WAS connected this time:
  download, phone-side hash verify, offer, and channel transfer
  all completed — the full Worker → phone → watch path is now
  proven end to end. What never arrived was the on-watch
  verdict: the Install tap (plus Android's system confirmation)
  either wasn't made within 300 s or the watch-side
  prompt/verdict path stalled; the evidence cannot distinguish
  those from here. Per the skill, a timeout means "not tapped
  yet", never an auto-retry — no duplicate was enqueued. The
  watch-side job may still sit prompted on the watch; a late tap
  can land the install without a matching command result.
- Next attempt precondition: watch on + connected (as #1), plus
  Jonathan watching the watch for the prompt when the enqueue
  lands — the effective tap window is the phone's 300 s verdict
  budget.

## Prerequisites

1. The PC (PowerSpec-G757) awake and reachable over the tailnet SSH
   forwarder (the fleet's `local-pc-lane`: `ssh -p 2222 -i
   ~/.ssh/id_ed25519 jonat@127.0.0.1`). If it's off: Wake-on-LAN via
   `~/workspace/bin/wol.py`.
2. On the watch: **Settings → Developer options → Wireless
   debugging ON**, and read the **"IP address & Port"** line on that
   screen (e.g. `192.168.1.182:41461` — the port will differ; never
   reuse an old one). Pairing persists from the face-deploy work, so
   no pairing code is needed — just the current port.
3. The built agent APK:
   `~/workspace/muse-wear-installer/app/build/MuseWearInstaller-v1.0.0.apk`
   (build it with `app/build.sh` if missing).
4. The phone running **Muse Wear Installer v1.0.1** (phone app) — the
   standalone phone app the `wear_install_face` command lives in
   since the 2026-11-11 split (project:
   `~/workspace/muse-wear-installer-phone/`; APK:
   `app/MuseWearInstallerPhone-v1.0.1.apk`, install it the usual
   phone-side way). Open it once: enter the Worker base URL +
   secret (the same pair the mode-sync lane uses), tap **Save &
   start lane**, and exclude it from battery optimization. (The
   verb originally shipped inside Muse Device Assistance
   v1.13.0; that build is withdrawn as the lane's owner — do not
   rehearse against it.) The watch bootstrap works without the
   phone app, but the end-to-end path needs both halves.

## Run

```bash
~/workspace/muse-wear-installer/bootstrap/bootstrap.sh 192.168.1.182:<current-port>
```

The script, in order:

1. Hashes the local APK and prints size + SHA-256.
2. Copies it to the PC (`Downloads\MuseWearInstaller.apk`) via scp
   over the forwarder.
3. Connects adb to the watch endpoint (chained into every remote
   call — the PC's adb daemon is per-session) and asserts the device
   identity: `getprop ro.product.model` must print **SM-L505U**
   (Galaxy Watch 8 Classic). If it prints anything else, stop.
4. `adb install -r` the agent APK → expect `Success`.
5. Grants the one permission the agent needs that ADB can grant:
   `cmd appops set --uid com.jonathancruzate.metamusewearinstaller
   REQUEST_INSTALL_PACKAGES allow`.
   *Why an appop:* `REQUEST_INSTALL_PACKAGES` is not a runtime
   permission, so `pm grant` refuses it; the per-app "install
   unknown apps" allowance is an appop. Without it, the watch app
   fails every job closed with `install_permission_missing`.
6. Reads back: `cmd appops get` must show
   `REQUEST_INSTALL_PACKAGES: allow`, and `dumpsys package` must
   show `versionName=1.0.0`.

## After the script

1. On the watch, open **Muse Wear Installer** from the app drawer
   once. It shows "Ready." — and Android may ask for the
   notification permission; allow it (the install prompt is a
   notification with Install/Dismiss actions).
2. First end-to-end rehearsal: enqueue one `wear_install_face`
   command with a known face APK (Muse does this from chat with a
   direct Worker API POST to the `/bridge` base —
   `https://jonathancruzate.com/bridge/phone-bridge/enqueue` with
   the wear lane secret — see the wear-installer SKILL.md. The old
   `phone-bridge-cmd.sh` / `MODE_SYNC_BASE` form is retired:
   `MODE_SYNC_BASE` was deleted 2026-11-11 and the script refuses
   it loudly). Expected sequence in the command result
   and the wearbridge report's `wear_install` object:
   `downloading → downloaded → offered → transferred → (watch)
   received → transferred → verified → prompted → installing →
   installed`. The watch shows the prompt; one tap on **Install**,
   then Wear OS shows its own system install confirmation (the
   PackageInstaller user-action step) — tap through it. That second
   system tap is Android's, not ours; v1 keeps it.
3. If any stage sticks, the watch app's idle screen shows the last
   job + stage, and the wearbridge report carries the same state.

## Notes

- The bootstrap does **not** set a watch face active and does not
  touch the Swiss Army packages.
- Upgrades of the agent itself later can ride its own channel only
  for *other* packages; replacing the agent still uses this script
  (an installer can install its own update too — PackageInstaller
  allows self-update with the same signing key — but the ADB route
  stays the documented fallback).
- Dad's Watch 8 Classic is out of v1 scope: the same bootstrap
  applies per watch when multi-watch support lands.

## /bridge migration completion — 2026-11-16 (addendum)

Supersedes the migration status in the Rehearsal #1 section above:
the `/bridge` zone route was fixed and verified 2026-11-15, and the
migration's final step ran 2026-11-16 (full record:
`BRIDGE-MIGRATION-2026-11-13.md` in the mode-sync tree). End state
for this lane: **the phone app stays dual-served by design** — it
holds the legacy pair (base `/mode-sync` + legacy shared secret),
has no transport-directive handler, and the wear lane's legacy
acceptance therefore remains OPEN. The shield lane's legacy
acceptance was retired (it had no direct Worker consumers); phone
legacy also remains open (MacroDroid Mode Poll macro). The
`/mode-sync*` route stays because this app (and the macros) still
address it. VM-side wear enqueues now use the `/bridge` base + the
wear lane secret directly against the Worker API (see the skill's
updated example). INTERLOCK for any future on-device change: base
and secret must change together, in one edit (skill, Setup step 1).

## v1.0.2 transport migration — 2026-11-16 (addendum)

Updates the addendum above: the phone app's missing piece shipped.
**Muse Wear Installer Phone v1.0.2** adds the transport-directive
handler (probe-before-switch, previous pair kept, 24 h probation
revert; TransportDirectiveTest 22/22, WearGateTest 16/16,
ProtocolTest 23/23) and was installed on the S24+ 2026-11-16
23:53:18 (vc3 / 1.0.2 read-back verified, firstInstallTime
unchanged; APK 423,238 B, signer = the project cert). The Worker
side was already live: arming KV `wearbridge:transport-directive`
attaches `{base: /bridge, secret: <wear lane secret>}` to every
wear-lane response.

**The switch has NOT happened yet.** The v1.0.1 lane service did
not survive the update (sticky FGS not restarted by Samsung; the
service is not exported, so shell cannot start it), no v1.0.2
report posted while the flag was armed, and the flag was disarmed
again per the gated plan — wear legacy acceptance remains OPEN,
`LEGACY_RETIRED_LANES` unchanged. Finish: tap **Save & start
lane** in the app (fields pre-filled; the tap only restarts the
lane), re-arm the flag, watch `wearbridge:report` for
`transport.base` = `jonathancruzate.com/bridge` + `switched`,
verify, then retire. Full record: the phone project's
`docs/v1.0.2-build-2026-11-16.md`.
