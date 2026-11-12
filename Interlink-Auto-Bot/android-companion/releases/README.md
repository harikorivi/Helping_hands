# Interlink Scheduler 2.0-rc2 — guarded task-removal recovery

[Download Interlink-Scheduler-2.0-rc2.apk](Interlink-Scheduler-2.0-rc2.apk). Version code 17, same package/signing identity. Install over the existing app without uninstalling. Node source from 1.8-rc2 or newer already has the required protocol; no new login is required when stored refresh credentials work.

This update handles the observed callback-only service initialization: if scheduling is enabled but the service has not been foreground-started, task removal requests one foreground start. A healthy service stays running; paused scheduling remains paused. `START_STICKY` and `stopWithTask=false` were already present in rc1. This is a best-effort recovery improvement, not force-stop immunity.

After updating, tap **Request Scheduler battery exemption**, approve Android's native dialog, and return to verify **Scheduler=true**. Keep Battery Saver off during the survival trial. Termux's background/battery access is separate. Tap **Start / repair 24-hour scheduling**, then use the **10-minute service timer test — no claim** and export diagnostics after the trial. Keeping the task in Recents is an operational comparison; the code does not assume that its padlock prevents every OEM kill.

Logs now identify service instances, PID-at-event, callback-time foreground/wake/enabled state and explicit app-owned stop reasons. An unexplained framework teardown is labeled without inventing a cause. [What the field evidence proves](../../docs/TASK-REMOVAL-REVIEW-2026-11-12.md) · [Executed validation](VALIDATION-2.0-rc2.md) · [Installation](../../README-mobile.md).

SHA-256: `ade1edbd359b34c628ead888bca396cdbff1211c04ba479f4590218987a6f463`.

---

## Previous 2.0-rc1 — one continuous foreground service

[Download Interlink-Scheduler-2.0-rc1.apk](Interlink-Scheduler-2.0-rc1.apk). Version code 16, same package/signing identity. Install over the existing app; do not uninstall. The user now accepts continuous background execution and its battery cost. This is a validation candidate, not a claim of vivo force-stop immunity.

Open Scheduler and tap **Start / repair 24-hour scheduling**. Confirm service running, CPU wake lock true and the persistent **Interlink Scheduler service** notification. Node still runs only for the six bounded sessions and manual requests. Stored authentication is retained; no re-login is needed if refresh works. Source from 1.8-rc2 or later supports wrapper protocol 2; see the single [installation guide](../../README-mobile.md) for new installs or existing-user updates.

One service replaces AlarmManager, the separate monitor and experimental cycling modes. It owns all six local-time slots, manual actions and correlated callbacks. Delayed/stale callbacks cannot stop the continuous scheduler. Foreground promotion precedes asynchronous diagnostics. Original deadlines, duplicate barriers and two independent verified results remain. Use the 2-minute or 10-minute **service timer test** to inspect survival; the probe itself launches no Termux command or claim. Exports remain available in Downloads.

All local checks passed, including actual emulator Recents removal and screen-off timing. Physical overnight/OEM survival and real Termux/API acceptance remain pending. [Architecture](../../docs/ADR-SERVICE-ONLY.md) · [Executed validation](VALIDATION-2.0-rc1.md).

SHA-256: `c6aaa9d9c2e405d429cf747c3c2c7ec220c717554eca6846f0da153c69ca219f`.

---

## Archived 1.8-rc4 — optional wake-lock cycling experiment

[Download Interlink-Scheduler-1.8-rc4.apk](Interlink-Scheduler-1.8-rc4.apk). Version code 15, same package and signing identity. Install over the current app. No Node source update is needed if already on the 1.8-rc2 source.

**Controlled experiment, not a proven OEM bypass.** Available device settings are accepted as enabled. The system requester com.vivo.abe is recorded in exit descriptions, but a four-minute counter/reset mechanism has not been established.

Stop continuous monitoring if running, enable **experimental wake-lock cycling**, then start continuous scheduling. Tap **Test service survival • in 10 minutes**, repeat the same swipe/lock actions as the previous trial, leave untouched for 12 minutes, then export the diagnostic log. Avoid overlapping a scheduled claim window. The diagnostic probe submits no claims. Logs include mode configuration, each cycle, timer firing and retained exit descriptions.

Cycling is off by default. It applies to Scheduler's monitor and bounded execution locks, preserves original deadlines and does not meaningfully reduce continuous-mode battery consumption. No unrelated special access or system-package disabling is added. [Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.8-rc4.md).

SHA-256: `d449cea5f661a6c3913060bc7d90b9d719deec1c5d6d5a74ec5b87f9a999d502`.

## Previous exit-description candidate 1.8-rc3

[Download Interlink-Scheduler-1.8-rc3.apk](Interlink-Scheduler-1.8-rc3.apk). Version code 14, same package/signing identity. Install over the current app. No Node update is required if already using the 1.8-rc2 source.

Open Scheduler and save the diagnostic log to Downloads. It rereads up to eight retained Android process-exit records and adds the public, nullable system description with redaction, reason name/code, status, importance and memory values. A new system bug report or reproduction may not be necessary to retrieve retained details. This cannot expose private system subreasons or guarantee that a description names the requester.

The former close-screen control is now **Background app, keep scheduling**, using normal backgrounding and preserving the task in Recents. This is a supported operational comparison, not proof of reliable execution after arbitrary swiping or OEM termination.

[Review assessment](../../docs/REVIEW-FOLLOWUP.md) · [Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.8-rc3.md).

SHA-256: `a88fe8869bf2ffa2ec49d40906f2936d7af2afe5c77dfba806b17931e5168f0a`.

## Previous task-removal candidate 1.8-rc2

[Download Interlink-Scheduler-1.8-rc2.apk](Interlink-Scheduler-1.8-rc2.apk). Version code 13, same package/signing identity. Install over the existing app; **update the Termux bot source too**. Wrapper protocol 2 is required; older source is reported as `bot_update_required` before any scheduled claim.

Continuous mode retains its foreground service after normal task removal and records `MONITOR_TASK_REMOVED`. A **Close screen, keep scheduling** action closes only the UI task. Existing sticky restart and notification controls remain. OEM termination and true force-stop cannot be overridden.

The scheduled Termux bootstrap checks project files, Node version, dependencies, flock/timeout and wrapper compatibility offline. Safe startup reasons replace generic missing-report failures. Historical exit 2 remains unexplained without its old private Node log; the new path diagnoses subsequent attempts.

[Upgrade guide](../../README-mobile.md) · [Validation](VALIDATION-1.8-rc2.md). Physical vivo overnight/service survival remains pending.

SHA-256: `7d97c736db9511ec7d9ada5749d0c3418ce5f02e68ce2b464e13460503b9d633`.

## Previous continuous candidate 1.8-rc1

[Download Interlink-Scheduler-1.8-rc1.apk](Interlink-Scheduler-1.8-rc1.apk). Install over the existing app; do not uninstall. Version code 12, same package/signing identity. If your Termux source is already 1.7-rc1 or newer, no Node update or re-login is needed.

Open the app, ensure the six daily schedule is enabled, then tap **Start continuous scheduling**. Confirm the battery cost. Keep the ongoing notification. It holds a CPU wake lock and runs a timer in the Android foreground service, so this mode uses more battery. Tap **Test continuous timer • in 2 minutes** for a no-claim locked-screen check, then save the diagnostic log to Downloads. Stop monitoring in the app or from its notification. Normal Android alarms remain registered; original deadlines and duplicate guards still apply.

This is a phone validation candidate. Emulator tests pass, but the affected vivo/Funtouch phone has not yet demonstrated a locked-screen scheduled run with the mode enabled. Android force-stop and OEM termination can still prevent execution. [Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.8-rc1.md).

SHA-256: `ddba3ff488d18b3fcade238bcc027283c3ea7ca31d06ea0326201c3c74f37c4f`.

## Previous continuous candidate 1.8-rc1

[Download Interlink-Scheduler-1.8-rc1.apk](Interlink-Scheduler-1.8-rc1.apk). Install over the existing app; do not uninstall. Version code 12, same package/signing identity. If your Termux source is already 1.7-rc1 or newer, no Node update or re-login is needed.

Open the app, ensure the six daily schedule is enabled, then tap **Start continuous scheduling**. Confirm the battery cost. Keep the ongoing notification. It holds a CPU wake lock and runs a timer in the Android foreground service, so this mode uses more battery. Stop it in the app or from **Stop monitoring** in the notification. Regular Android alarms remain registered, and original deadlines plus duplicate guards still apply.

This is a phone validation candidate. Emulator tests pass, but the affected vivo/Funtouch phone has not yet demonstrated a locked-screen scheduled run with the mode enabled. Android force-stop and OEM termination can still prevent execution. [Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.8-rc1.md).

SHA-256: `2350ad608e5f4f78bd8fe98b4c4a8a1486a30d73ca048d1eec9b4c61a6e6c23e`.

## Previous comparison candidate 1.7-rc4

[Download Interlink-Scheduler-1.7-rc4.apk](Interlink-Scheduler-1.7-rc4.apk). Install over the existing app. Version code 11, same package and signing identity. No Node update or login needed when already on the 1.7-rc1 Node source.

**One next action:** enable Live debug logging, tap **Test alarm-clock delivery • in 10 minutes**, then lock the phone and leave it untouched for 12 minutes. Save the diagnostic log to Downloads afterward. This probe uses `setAlarmClock()` and posts a delivery notification; it submits no claim. A system upcoming-alarm indicator may appear. Cancel using **Cancel background alarm test**.

Six daily claim runs remain unchanged pending physical validation. This is a comparison build, not a proven Funtouch fix. [Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.7-rc4.md).

SHA-256: `d1055a06b6188816ae12ea1aab2c48a47f68692b4a36975b0054f0534caa2c80`.

## Previous candidate 1.7-rc3

[Download Interlink-Scheduler-1.7-rc3.apk](Interlink-Scheduler-1.7-rc3.apk). Install over the existing app; do not uninstall. Package `ai.interlink.scheduler`, version code 10. Existing 1.7-rc1/rc2 Node source does not need another update for these Android-only features. No re-login required.

Enable **Live testing diagnostics → Live debug logging**, then optionally tap **Record system state now**. After testing, use **Save diagnostic log to Downloads** and share the text file from **Downloads → Interlink Scheduler**. Turn extra debugging off when finished; core execution evidence stays on.

Records app visibility, observed screen/unlock/power transitions and system state while the process exists. It cannot observe events while killed and cannot distinguish Home from swipe solely from activity visibility. **This does not fix or establish the cause of delayed alarm delivery on unlock.**

[Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.7-rc3.md).

SHA-256: `ba8ad13bc4702f00a98bb3109f08030f3f87a96975f226bd5f8ec59e1cd8159d`.

## Previous export candidate 1.7-rc2

[Download Interlink-Scheduler-1.7-rc2.apk](Interlink-Scheduler-1.7-rc2.apk). Install over the existing app; do not uninstall. Version code 9, package `ai.interlink.scheduler`, same signing identity. If already using the 1.7-rc1 Node source, this feature only needs the APK update; no login or Node change is needed.

Use **Execution evidence → Save diagnostic log to Downloads**. Android 10+ saves a text log under **Downloads → Interlink Scheduler**; Android 8/9 uses the save picker. Share the file from File Manager. No storage permission, USB pairing or large clipboard paste is needed. Saved exports remain until manually deleted.

This is an export update, **not a fix for delayed alarms on unlock**. Physical-device reliability remains unvalidated. [Installation guide](../../README-mobile.md) · [Validation](VALIDATION-1.7-rc2.md).

SHA-256: `cd81371f7765c7aae61bb897efb23d6fb3734d064090e27fdfdf6b6eaec6eda8`.

## Previous candidate and architecture evidence

### Interlink Scheduler 1.7-rc1 — validation candidate

Install [Interlink-Scheduler-1.7-rc1.apk](Interlink-Scheduler-1.7-rc1.apk) **with the matching current Node.js source**. Follow the [installation and upgrade guide](../../README-mobile.md). Working stored credentials do not require re-login.

**Not production-validated. The physical phone’s missing-alarm root cause remains open.** This candidate fixes confirmed diagnostic and lifecycle gaps; it does not guarantee uninterrupted Android execution. Four-hour and overnight physical tests remain release blockers.

Personal debug-signed APK: package `ai.interlink.scheduler`, version code 8, minimum Android 8. Credentials are not bundled. Use the same private signing key for compatible updates; never commit keys. Older APKs are historical releases.

Includes correlated persistent lifecycle events, independent claim-window outcomes, safe Node-to-companion reports, explicit unknown/error states, delivery statistics, structured export, and 4h/8h no-claim probes. See the [audit](../../docs/RELIABILITY-AUDIT.md), [changelog](../../docs/RELIABILITY-CHANGELOG.md), and [T1–T18 matrix](../../docs/RELIABILITY-VALIDATION.md).

[Candidate verification](VALIDATION-1.7-rc1.md): 34 Node tests, 9 JVM tests, 13 normal emulator tests plus one separate forced-Doze test passed. Build/lint passed (0 errors, 34 warnings). APK v2 signature verified. This is not full Termux/physical-phone testing.

SHA-256:

```text
45e93a74c613ec99895468d4e1ba974a2b204cb661024c740984c334066a6667
```

## Separate offline diagnostic app

[Alarm-Delivery-Check-1.0.apk](Alarm-Delivery-Check-1.0.apk) installs alongside the scheduler and needs no USB/network pairing, Termux or account. It tests its own alarm delivery and copies a report for manual sharing. It cannot read privileged system logs or another app's private state. See [instructions](../alarm-diagnostics/README.md) and [validation](VALIDATION-ALARM-CHECK-1.0.md).
