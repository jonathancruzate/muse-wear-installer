# Muse Wear Installer — design

**Status: v1.0.0 built 2026-11-11; bootstrap (first on-watch install)
pending.** Built at Jonathan's order 2026-11-11 to end the ADB era
for watch-face installs.

## The problem

Pushing a watch face (`.apk`) to the Galaxy Watch 8 Classic meant:
wake the PC, find the watch's Wi-Fi debugging endpoint — whose port
**rotates** — connect adb, install. On 2026-11-10/11 that lane
failed twice in one night (PC hung with a dark NIC, then the port
rotated). Face installs are frequent (the Swiss Army iterations), so
the lane had to go.

## The shape

```
Muse (chat) ── command channel ──> Muse Device Assistance (phone)
                                        │ wear_install_face
                                        │ {label, url, sha256}
                                        ▼
                              download APK over HTTPS,
                              verify sha256 on the phone
                                        │ Wearable Data Layer
                                        │ offer (MessageClient)
                                        │ APK bytes (ChannelClient)
                                        ▼
                              Muse Wear Installer (watch)
                              re-verify sha256 → prompt
                              [Install] / [Dismiss]
                                        │ Install
                                        ▼
                              PackageInstaller session →
                              system confirmation → verdict
                                        │ status (MessageClient)
                                        ▼
                         phone posts the Worker command-result;
                         Muse in chat reads the verified outcome
```

The trigger rides the fleet's existing command channel unchanged
(structured `{template, args[]}` enqueued via
`phone-bridge-cmd.sh`, gated on-device by ShellAllowlist — the
transport cannot widen the allowlist). The new template is
`wear_install_face`; the phone half shipped as Muse Device
Assistance **v1.13.0**. The watch half is this project, its own app
— not a plugin of the phone app, because it lives on a different
device; the pattern (host stays slim, separate APK owns the device
semantics, contract strings duplicated by copy) follows the Galaxy
Buds plugin precedent.

## The wire protocol

Defined once in `Protocol.java` (watch) and copied verbatim as
`WearProtocol.java` (phone). Flat JSON, written/parsed by the small
hand-rolled pair in that class — no JSON library on either end, so
both ends behave identically and the logic is JVM-unit-tested
(`tools/ProtocolTest.java`, 23 assertions).

| Leg | API | Path | Payload |
|---|---|---|---|
| offer | MessageClient | `/muse-wear-installer/offer` | `{job_id, label, sha256, size, protocol:1}` |
| payload | ChannelClient | `/muse-wear-installer/apk/<job_id>` | raw APK bytes |
| status | MessageClient | `/muse-wear-installer/status` | `{job_id, label, stage, detail, ts}` |

Stages: `received → transferred → verified → prompted →
installing → installed`, with terminal failures `failed`,
`verify_failed`, `dismissed`. The watch persists every stage in its
JobStore; the phone mirrors them into its report's `wear_install`
object. Offer and channel may arrive in either order; the watch
joins them in JobStore. One job at a time — a second offer while a
job is mid-flight is refused `busy`.

Trust: the sha256 in the (already-authenticated) command is the
root. The phone verifies after download; the watch verifies after
transfer, before prompting. A mismatch fails closed on both ends.

## Decisions

1. **PackageInstaller sessions, not a FileProvider content URI.**
   The task brief's parenthetical suggested handing the APK to
   PackageInstaller via a FileProvider URI. The session API is the
   same PackageInstaller, takes the bytes by stream (no exported
   file, no androidx FileProvider — this build is framework-only),
   and reports through the same status receiver. The one permission
   the flow needs, `REQUEST_INSTALL_PACKAGES`, is unchanged.
   Android still shows its own system install confirmation after
   the watch app's prompt (the `STATUS_PENDING_USER_ACTION` step) —
   that second tap is the platform's, kept deliberately in v1.
2. **GMS vendored as jars, no Gradle.** Fleet builds are raw
   aapt2/javac/d8. The pinned set: play-services-wearable 19.0.0,
   base 18.5.0, basement 18.4.0, tasks 18.2.0 (AARs + POMs staged in
   `app/libs-src/`, extracted `classes.jar`s in `app/libs/`).
   `google_play_services_version` (12451000) is read from the
   basement AAR's own resources and defined in each app's
   `res/values/gms.xml`, since the raw toolchain does no AAR
   resource merging.
3. **AndroidX: two narrow vendored exceptions.** androidx.annotation
   (the `-jvm` artifact; annotations never execute) and
   androidx.collection **1.1.0** — the last pure-Java release; GMS's
   IPC internals (ClientSettings, GoogleApiManager's `zal`,
   AvailabilityException) genuinely use ArrayMap/ArraySet, so these
   classes must be defined. Traps recorded: the `collection` and
   `annotation` 1.4.5/1.9.1 `.jar` artifacts on Google Maven are
   Kotlin-multiplatform *metadata* jars containing zero Android
   classes; only `-jvm`/older artifacts carry bytecode. GMS's
   error-dialog helper code also *references* androidx.activity /
   androidx.core / androidx.fragment — never vendored: those classes
   sit behind GMS's UI error-resolution paths that the Data Layer
   IPC never loads, and ART verifies lazily per class. Both builds
   count and log those dangling references in their guards rather
   than silently passing them.
4. **The allowlist's dedicated validator.** `wear_install_face`
   carries an HTTPS URL, which can never satisfy the shared shell
   hygiene (it bans `?`, `&`, `=` and caps args at 256 chars — bans
   that exist because shell argv is space-joined). These args never
   become shell text, so `ShellAllowlist.checkWearInstall` validates
   them per slot instead: strict label charset, https-only URL with
   a host, no userinfo/whitespace/quotes/control chars, ≤2048 chars,
   exact lowercase sha256. The allowlist test suite carries 11 cases
   for it.
5. **Node pick.** Phone → watch node: prefer a connected node whose
   display name contains "watch" (case-insensitive); else the single
   connected node. Statuses return to all connected nodes.
6. **Timeouts are verdicts, not retries.** The phone waits up to
   300 s for the terminal stage. A timeout returns
   `watch_install_timeout` with the last stage — the user may simply
   not have tapped yet; the watch keeps its prompt and its JobStore
   record, and the report shows the truth afterwards.
7. **Signing.** Project-local keystore
   (`app/keystore/musewearinstaller.keystore`, auto-created by
   build.sh on first run, fleet pattern), so agent upgrades install
   in place. Cert SHA-256
   `07dca87043c69917ab8be25202744698f4fa777e27840a674541cbd5ce5705e8`.

## Not in v1

- **Multi-watch.** Dad's Watch 8 Classic needs the same bootstrap +
  a node-pick by watch identity. The protocol is already node-scoped
  per send; the router picks one node today.
- **Face activation.** Installing a face APK never selects it; the
  Wear OS picker does (same as the ADB lane).
- **Removing the system install confirmation** (would need a
  privileged/device-owner posture the fleet doesn't take).
- **QUEUED jobs / history** beyond the last job's record.

## Verification state (2026-11-11)

Proven off-watch: full raw-toolchain builds of both APKs with all
dex guards green; manifest/permissions/component audits of both
APKs; JVM tests — ProtocolTest 23/23, ShellAllowlistTest all-pass
including the 11 new cases; the protocol logic byte-identical
between the two codebases (checked by diff).

**Not yet proven — everything on a real watch.** The Data Layer
delivery to the actual Watch 8 Classic, the prompt rendering, the
PackageInstaller session behavior on Wear OS, and the status return
path are all unverified until the bootstrap install
(`bootstrap/BOOTSTRAP.md`) and one rehearsal command run. This
environment has no working Wear OS emulator (the SDK carries no
emulator; the fleet's 2026-10-31 emulator boot never completed), so
the first rehearsal is the real device test — staged, deliberate,
and read back stage by stage.
