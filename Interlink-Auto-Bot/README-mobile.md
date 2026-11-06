# Interlink Scheduler — first-time Android installation

This guide installs **version 1.7-rc3 (validation candidate)** for a new user. **Not production-ready: physical-device four-hour and overnight acceptance is pending.** Read the [audit](docs/RELIABILITY-AUDIT.md) and [validation plan](docs/RELIABILITY-VALIDATION.md). Everything needed is in this repository: the Node.js bot, authentication and eligibility handling, on-demand actions, and the Android companion app. Follow the steps in order; no patch ZIPs or text editor are needed.

The system has two components on your phone:

- **Termux** runs the Node.js bot and stores your credentials privately.
- **Interlink Scheduler** is the companion APK with a teal clock icon. It schedules execution and provides **Test connection now** and **Claim once now** buttons. It is not the official Interlink app.

You do not need cron, Termux:Boot, Termux:API, a continuously running Node process, or a connected Mac after installation. Android background execution remains subject to battery restrictions and phone manufacturer policies. Do not force-stop either app.

> **Already using the bot?** Follow the [existing-user note](#note-for-existing-users) instead of cloning again or logging in again. Keep all existing credential and device files.

## Before you begin

You need an Android 8 or newer phone, internet access, a supported Termux build with `RUN_COMMAND` support, and your Interlink login/passcode plus access to email OTP. Use a maintained Termux release; if you use add-ons, install them from a consistent signing source. Compatibility varies across Termux distributions and phone manufacturers.

Install Termux and open it once. Unless a step explicitly says otherwise, run commands **inside Termux on your phone**, not on the Mac. Copy commands without any terminal prompt such as `~ $`. If a previous paste left you at a `>` prompt, press **Ctrl+C** before continuing.

## 1. Install the required packages

Run one command at a time:

```sh
pkg update
```

```sh
pkg install nodejs-lts git util-linux coreutils
```

The bot requires Node.js 22 or newer. You can check your installed version:

```sh
node --version
```

## 2. Download the project

Use this exact location because the companion APK expects it:

```sh
cd ~
```

```sh
git clone https://github.com/harikorivi/Helping_hands.git
```

```sh
cd ~/Helping_hands/Interlink-Auto-Bot
```

If the repository already exists, use the existing-user note below. Do not delete an existing checkout to repeat installation.

## 3. Run the Termux setup

```sh
bash setup-termux.sh
```

Wait until it prints **Setup complete**. If it fails, resolve the displayed error before continuing.

The setup command:

- Installs the locked Node.js dependencies and runs mocked tests (no real claims).
- Creates `~/.termux/termux.properties` if missing, or backs it up if it exists.
- Preserves other settings and ensures one active `allow-external-apps=true` line.
- Reloads Termux settings and prepares the execution scripts.
- Preserves credentials and prevents setup while a bot execution holds the lock.

No nano, vi, or manual file editing is needed. To inspect the resulting setting:

```sh
cat ~/.termux/termux.properties
```

You should see an uncommented line:

```properties
allow-external-apps=true
```

This allows authorized apps to request Termux commands. Grant that permission only to apps you trust.

## 4. Log in for the first time

```sh
node index.js --login
```

Follow the prompts for login ID, passcode and email OTP. Passcode and OTP input are hidden, so typed characters will not appear. Press Enter after each answer.

Successful authentication stores the access and refresh tokens together in private `credentials.json`, with the device identity in `device.txt`. Future runs check JWT expiration and automatically refresh expired or soon-to-expire tokens. A rotated refresh token is saved atomically with the access token before execution continues. Unattended execution never prompts for OTP.

Do not share or commit `credentials.json`, `token.txt`, `refresh_token.txt`, or `device.txt`. Keep the project in Termux's private home, not shared Android storage. Never disable TLS certificate verification.

## 5. Install the companion APK

On your phone, open [Interlink-Scheduler-1.7-rc3.apk](https://github.com/harikorivi/Helping_hands/raw/refs/heads/main/Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-1.7-rc3.apk) and download it. The same file is in the repository at:

```text
Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-1.7-rc3.apk
```

Open the downloaded APK using your browser or Files app. If Android asks, allow **Install unknown apps** for the browser/Files app you are using, then select **Install**. You can turn that installation permission off afterward.

Find **Interlink Scheduler** by its teal clock/check-mark icon and open it. This is a personal debug-signed APK, not a Play Store release. No Mac build is needed to use the supplied APK.

## 6. Complete the app's four setup sections

Tap each button **once**. Return to the app after changing an Android setting; status updates automatically. If a setting already shows **Allowed**, you do not need to grant it again.

| Section | App to configure | What to do |
|---|---|---|
| **1. Allow Termux commands** | **Interlink Scheduler** | Allow **Run commands in Termux environment**. If the prompt does not reopen, use Scheduler App info → Permissions → Additional permissions. |
| **2. Allow scheduled alarms** | **Interlink Scheduler** | Open **Scheduler alarm settings** and allow **Alarms & reminders**. Android versions before 12 do not need this special permission. |
| **3. Allow both apps in background** | **Interlink Scheduler AND Termux** | Use each named battery button. Select Battery / App battery usage → Unrestricted / No restrictions / Allow background activity, as offered by your phone. |
| **4. Show run notifications** | **Interlink Scheduler** | Open **Scheduler notifications** and allow notifications so active executions are visible. |

For a separate battery-optimization list, tap **Open battery optimization list**, select **All apps** if needed, and choose **Don't optimize** for **both Interlink Scheduler and Termux**. Do not select the official Interlink app for these settings.

Some phones also require Auto-start permission or an additional background-activity setting. Automatic scheduling requires Android battery-optimization exemptions for both apps; the Enable/repair button explains any missing prerequisite. Manufacturer-specific restrictions still need checking separately. Settings links open the named app's App info with guidance; if a shortcut is unavailable, navigate through Android Settings → Apps manually.

## 7. Test the installation immediately

In the app's **Try it now** card, tap:

**Test connection now • no claim**

Wait for the result in that card, allowing up to two minutes. The test starts Termux, validates/refreshes authentication, and reads account and eligibility information. It does **not** submit a claim. A successful test confirms the foreground execution path; it does not yet prove alarm delivery while the phone is locked.

If the test fails, inspect the private log in Termux:

```sh
cd ~/Helping_hands/Interlink-Auto-Bot
```

```sh
tail -n 40 "logs/on-demand-$(date +%Y-%m-%d).log"
```

Logs report safe metadata such as request stage, HTTP status and field types. Do not share raw API response bodies or credential files.

> Manual actions reserve **:24 through :33** during hours **01, 05, 09, 13, 17 and 21** for automatic execution, even when the schedule is paused. If the app asks you to defer, try after minute 34. An existing run also prevents overlapping execution.

## 8. Enable automatic scheduling

Tap **Enable schedule**. Confirm that the top card says **Schedule is enabled** and shows the expected **Next run** in your phone's local time.

| Run starts | Check the log after |
|---|---|
| 01:26 AM | 01:35 AM |
| 05:26 AM | 05:35 AM |
| 09:26 AM | 09:35 AM |
| 01:26 PM | 01:35 PM |
| 05:26 PM | 05:35 PM |
| 09:26 PM | 09:35 PM |

Each run has two independent windows: **:26:00 through :29:59**, and **:31:00 through :33:59**. It attempts at most one submission per window, only if the server reports eligibility. Node exits after the windows; no Node process or companion execution service is required between the six daily runs. Node may sleep between the two windows within a run.

Press **Home** and turn off the screen. The app screens do not need to remain open. Swiping Recent Apps may work, but test it on your phone because manufacturer behavior varies. **Do not Force stop Termux or Interlink Scheduler.** Reopen the apps and repair the schedule after a force-stop. Reboot recovery rearms future alarms after normal unlock; missed windows are not replayed.

After the next run finishes, check:

```sh
cd ~/Helping_hands/Interlink-Auto-Bot
```

```sh
tail -n 60 "logs/claim-$(date +%Y-%m-%d).log"
```

Look for both `WINDOW_1_*` and `WINDOW_2_*` results:

- `SUCCESS`: API acceptance and an advancing claim timestamp were observed.
- `NOT_ELIGIBLE`: the server did not report eligibility during the checks.
- `FAILED`: inspect the reason. An uncertain submission may have been accepted by the server and must not be blindly retried.

The scheduled maximum is 12 successful claims per day, subject to eligibility and actual execution. The `logs` directory is created when an execution wrapper starts, not by the tests. An app message saying a command was sent is not proof that a claim succeeded. Old pause events in Recent activity are history; the top card shows current status. Each alarm now records intended time, actual delivery time and delay. Tests, manual claims and scheduled runs have different completion messages. Use **Copy diagnostic history** to copy the retained history and all six registered alarm times without tokens.

## Optional: submit one real claim now

Tap **Claim once now** and read the confirmation dialog. Confirm only if you intend to submit a real claim. The action checks eligibility, submits at most once and verifies the result, without waiting for a second window. It can affect eligibility for later scheduled runs.

Manual claims have a one-minute cooldown and share the bot's execution lock. An uncertain submitted manual claim blocks further manual claims pending review of the actual server outcome. Do not delete `.runtime/manual.json` to bypass that protection. Separate manual claims are not included in the scheduled maximum of 12.

For terminal use, `bash run-on-demand.sh test` performs the connection test. `bash run-on-demand.sh claim` requests a real claim without a confirmation prompt; use the app's confirmed button when learning the system.

## Automatic log cleanup

Version 1.6 keeps **today's and yesterday's logs**, using your phone's local calendar dates—not a rolling 48-hour interval. For example, on 8 November, logs dated 8 and 7 November remain; recognized daily logs dated 6 November or earlier are deleted.

- **Termux:** at the start of each scheduled or on-demand execution, under the shared execution lock, cleanup removes older `logs/claim-YYYY-MM-DD.log` and `logs/on-demand-YYYY-MM-DD.log` files. It does not scan other directories or follow symlinks. A cleanup error is logged without blocking the claim/test execution.
- **Companion:** old activity entries are removed when the app opens (even while scheduling is paused) and whenever a new activity event is recorded. UTC event timestamps are converted to the phone's local date. The existing limit of 100 stored entries still applies, so very busy days may retain fewer than two complete days. The UI shows the latest 20 entries.

Credentials, `.runtime` claim records, execution locks, scheduling preferences, and the latest action status are not deleted. Unknown filenames/formats and future-dated entries are preserved conservatively; app history still has its 100-entry cap. Future dates can occur after a clock correction. Cleanup takes place at the next app opening or execution, without a separate midnight timer or background daemon.

Existing users must update **both the Termux repository and the version 1.6 APK** to enable cleanup in both places. No re-login is needed.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Permission button does not show another prompt | It may already be allowed, or Android may suppress repeated prompts. Open Scheduler App info → Permissions. Tap once; double-tapping is unnecessary. |
| Token refresh fails | Check connectivity and the log. If reauthentication is required, pause scheduling, wait for the active run to finish, then run `node index.js --login`. |
| Unexpected eligibility response | The current code accepts HTTP 2xx with an explicit nested eligibility boolean; missing application status is allowed, but explicit errors/malformed fields are rejected. Share the safe `TEST_INVALID_RESPONSE` field-type diagnostics, not raw account data. |
| `missed_window` | The run started after that window closed. A manually started late run does not prove an Android alarm delay. |
| No scheduled log after minute 35 | Verify the companion's next alarm, permissions, both battery settings and the fixed checkout path. Inspect Recent activity for dispatch failure. |
| Command/file missing | Confirm `~/Helping_hands/Interlink-Auto-Bot/run-on-demand.sh` exists and run setup again. Use the APK and source from this same repository version. |
| Another run is active | Wait for it to finish. Do not remove the lock file to force overlap. |
| Schedule stops after permission changes | Regrant the required permission, reopen the companion and tap **Repair next scheduled run**. |

## Note for existing users

You do **not** need to clone again, delete tokens, or log in again if your existing credentials can authenticate or refresh. `credentials.json` takes precedence once present; otherwise legacy `token.txt` and `refresh_token.txt` are read with the existing `device.txt`.

1. Pause future runs in the companion and let any active execution finish.
2. Update the existing checkout and run setup:

```sh
cd ~/Helping_hands
```

```sh
git pull --ff-only
```

```sh
cd Interlink-Auto-Bot
```

```sh
bash setup-termux.sh
```

If Git reports local changes or conflicts, stop and preserve those changes before resolving them. Do not use a hard reset or delete the checkout: it may contain your credentials. Earlier manually copied fixes can cause such conflicts.

3. For the reliability update from 1.3 or earlier, **both the Termux source and the APK must be updated**. The fix replaces a suspend-sensitive clock comparison and changes single chained alarms to six independent daily slots. Stored credentials and claim ledgers are preserved. Late delivery never creates a catch-up claim, and delayed completion events identify their original run.
4. Install the current repository APK over the old app using **Update**, without uninstalling first. Updates require a matching signing key; if Android reports a signature mismatch, resolve that before proceeding.
5. Run **Test connection now • no claim**, then enable/repair the schedule. Only reauthenticate if your stored credentials cannot refresh.

### Only if migrating from the old cron setup

Remove the tagged Interlink entry while preserving unrelated jobs:

```sh
if crontab -l > "$TMPDIR/interlink-crontab-before" 2>/dev/null; then
  sed '/# interlink-scheduled-claim/d' "$TMPDIR/interlink-crontab-before" | crontab -
fi
```

```sh
crontab -l
```

If an untagged Interlink entry remains, identify its exact line before removing it. Do not enable both cron and the companion for this bot.

Find old cron startup scripts:

```sh
grep -n 'crond' ~/.termux/boot/* 2>/dev/null
```

Use `cat` with the reported filename to inspect it. If a script contains only this bot's cron startup and no other jobs need cron, move that script out of `~/.termux/boot/` into a backup directory and stop crond with `pkill -x crond`. Preserve unrelated startup commands and keep cron running if other jobs use it. Do not run `setup-schedule.sh` for the Android companion installation.

## Building the APK from source (maintainers)

New users can install the supplied APK; building is optional. The source uses SDK 35, JDK 17, AGP 8.7.3 and Kotlin 2.0.21. Gradle 8.11.1 is the locally verified build version. Install those tools or use your existing Android toolchain; this repository does not bundle a Gradle wrapper.

From the repository root on the build machine:

```sh
cd Interlink-Auto-Bot/android-companion
```

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Verify its signature with the SDK's `apksigner` before distributing it. Keep a consistent private signing key for updates; never commit that key. The published personal build is debug-signed. Android UI and settings navigation have been inspected in an emulator; end-to-end background claims on the user's physical phone still require the acceptance checks.

See [ANDROID-ARCHITECTURE.md](ANDROID-ARCHITECTURE.md) for the architecture review, security model, file responsibilities, alternatives, and testing matrix.

## Reliability behavior in 1.4

All six daily alarm slots are registered with Android at once; delivery of one alarm is not required to register the next slot. Each delivered slot rearms its own next-day occurrence. Reboot, app update, clock/timezone changes and reopening an overdue schedule rebuild future alarms. Recovery logs are explicitly different from alarm-delivery logs. Duplicate or replaced alarm intents do not launch another command.

The Node runtime uses a Linux/Android suspend-aware uptime source instead of `performance.now()` when detecting real clock changes. It checks the original wall-clock window before every HTTP request and on each resumed wait. It no longer represents a clock problem as infinite time or falsely labels a future window missed. Real clock/timezone changes receive a specific failure reason.

The companion checks both wall-clock and Android elapsed-realtime deadlines. If Android suspends the process, it cleans up on the first available callback after resume; it cannot execute cleanup while the OS refuses CPU time. The Node runner independently refuses late requests. An in-flight request may already have been accepted by the server and is never blindly retried. A missing callback does not block future alarms; delayed callbacks cannot terminate a newer run.

`run-scheduled.sh` logs entry before checking the execution lock. A busy lock returns a distinct non-success result. The log therefore distinguishes a wrapper that started but was busy from a command that never reached Termux. The intended alarm timestamp travels through the wrapper, preventing a very late command from being treated as a fresh scheduled window.

Emulator and simulated-clock tests improve confidence but do not guarantee OEM overnight behavior. After upgrading, verify one locked-screen scheduled execution and use **Copy diagnostic history** if it fails. Never force-stop either app.


## Background alarm diagnosis and recovery (1.6)

Exact-alarm **capability** is Android's `canScheduleExactAlarms()` result. It can be available through a battery exemption; it does not prove that the separate Alarms & reminders toggle is granted, nor that an alarm was delivered. See [Android AlarmManager](https://developer.android.com/reference/android/app/AlarmManager#canScheduleExactAlarms()).

For a no-claim background test:

1. Complete setup for both apps. Tap **Test background alarm • in 2 minutes** once. It also works with the daily schedule paused.
2. Press Home and lock the screen. After three minutes, reopen the app and copy diagnostic history.
3. Look for **Background test alarm delivered**, **Run service entered**, command dispatch and the connection result. Registration alone is not success. The test uses the same exact-alarm mechanism and Termux execution service as scheduled runs, but never invokes claim mode.
4. Repeat separately after swiping the scheduler from Recent Apps. Record which condition you tested. Do not force-stop either app. Avoid testing around the daily claim windows (minutes 24–34 at scheduled hours); the bot reserves those minutes and reports a deferred test. Frequent idle tests can also be affected by Android alarm throttling.
5. If there is no delivery, send diagnostic history including device/version information. A delayed delivery is recorded even if it is too late to execute. This test has a two-minute execution deadline from its intended alarm time.

A pending test can be cancelled independently. Pausing future runs cancels pending diagnostic alarms too; an already dispatched command may finish.

Opening or manually repairing an enabled schedule during its original :26–:34 execution period now attempts the remaining windows. At 13:29:35 the original 13:26 timestamp is retained, and execution must still stop at 13:34. Closed windows are skipped, and the shared lock and persistent claim ledger prevent duplicate submissions. This is recorded as **foreground recovery**, not alarm delivery. Opening after :34 never replays expired claims. Background reboot/time-change recovery rearms future slots; it does not launch a catch-up service.

**Known limitation:** the missing alarm on the reported iQOO 7/Funtouch OS 14 has not been reproduced conclusively on that device. Version 1.6 fixes foreground recovery and adds diagnostics; it does not claim to cure manufacturer-specific suppression or force-stop behavior.

Existing users: pause the schedule, let an active run finish, update the repository and install the 1.6 APK over the existing app. Open it and repair the schedule. If Git reports local patch conflicts, preserve them with `git stash push --include-untracked -m "Phone backup before update"`, then `git pull --ff-only`; do not reapply old patches automatically. Keep the stash as a backup. Credentials ignored by Git remain in place; no new login is required if they still work.

### When USB and wireless debugging are unavailable

Use the separate [Alarm Delivery Check app](android-companion/alarm-diagnostics/README.md). Its offline two-minute/ten-minute tests record actual receiver delivery and app-opening times, then provide a copyable report. It needs no network or Termux access and makes no claims. It tests its own alarms, not another app's private state, so results must be compared with the scheduler's diagnostics.


## Migrating to the evidence-driven validation candidate (1.7-rc3)

Pause the schedule and let the current run finish. In Termux:

```sh
cd ~/Helping_hands
git pull --ff-only
cd Interlink-Auto-Bot
bash setup-termux.sh
```

If Git refuses because of older local patches, preserve them with `git stash push --include-untracked -m "Phone patches before diagnostic candidate"` and retry the pull. Do not automatically pop old patches onto the current implementation. Ignored credentials/claim state remain private and untouched.

Install **Interlink-Scheduler-1.7-rc3.apk** over the existing app using the same signing key. Do not uninstall first. Open the app, repair once and enable the schedule. Both Node source and APK must be upgraded: an old wrapper returns no structured result, which is displayed as **UNKNOWN**, even if its exit code is zero. No re-login is required if stored credentials still refresh.

The **Execution evidence** card shows delivery statistics, last success/failure/miss/unknown, and each reported window's outcome and reason. **Copy structured execution report** exports JSONL. **Copy diagnostic history** also includes this evidence. Reports do not include raw HTTP responses, tokens, account information, passcodes or OTPs. Android environment snapshots belong to the event time; Node events have `device=null` and a separately labelled `deviceAtImport` snapshot because Termux cannot observe that historical Android state.

If a run was dispatched but no callback arrives, the dashboard marks it unknown at the next reconciliation. It never assumes a timeout proves that the server rejected a claim. Submitted markers remain in the private ledger; no uncertain submission is blindly retried. Compare the private `logs/claim-YYYY-MM-DD.log` when callback evidence is unavailable.

Use the new **4-hour** and **8-hour** connection probes for preliminary long-idle testing. They make no claim, but only one probe may be pending. These probes cannot replace validation of six daily scheduled runs and both real claim windows. Follow T1–T18 in the validation plan before accepting this candidate.

Event details are retained for today/yesterday, bounded at 4,000 Android events. Aggregate per-run outcomes are retained for 30 days for reliability statistics; this does not prune the Node claim ledger. Node callback reports under `.runtime/reports` are diagnostic-only and cleaned on a later run after their local-day retention expires. Power broadcasts are recorded only while the process exists; the app does not keep itself alive to observe them.


### Save a diagnostic log without copying text

In **Interlink Scheduler 1.7-rc3**, open **Execution evidence → Save diagnostic log to Downloads**. Wait for the saved confirmation. On Android 10 and newer, open your phone's **Files / File Manager → Downloads → Interlink Scheduler** and share the newest `Interlink-diagnostics-*.txt` as a file attachment. No USB, pairing, clipboard paste or storage permission is needed. Android 8/9 instead opens a file picker; choose Downloads and tap Save.

The file contains companion activity, current device state, scheduled times, retained execution summaries and structured events. It does not include raw Termux logs or credential files. It includes device information and execution times, so share it only with the intended recipient. Exported copies remain until you delete them; the app's two-day event cleanup does not delete Downloads exports.

This export update does not fix the reported alarm delay until screen unlock. The user observed delayed background execution without opening Scheduler; treat that separately from foreground app-open recovery. The precise OS cause remains unconfirmed.


### Configurable live testing (1.7-rc3)

1. Open **Interlink Scheduler → Live testing diagnostics** and enable **Live debug logging**. It is off by default and remembers your selection.
2. Tap **Record system state now** to mark your test's starting conditions.
3. Start one background test, then leave the app and lock the phone. Note the intended time and the time you unlock. Avoid scheduling repeated probes near a real scheduled run.
4. After the test, open Scheduler and select **Save diagnostic log to Downloads**. Share the newest text file from **Downloads → Interlink Scheduler**. You do not need to paste its contents.
5. Turn **Live debug logging** off after testing. Core alarm, execution and claim-result logging continues.

Extra events include `APP_FOREGROUND`, `APP_BACKGROUND`, `SCREEN_ON_OBSERVED`, `SCREEN_OFF_OBSERVED`, `USER_UNLOCK_OBSERVED` and observed power changes. Events include interactive/lock state, idle state, battery saver, charging, standby bucket, network state, process ID, elapsed time, model and Android version. The manual snapshot works even with live debug off.

App background means the activity is no longer visible; it cannot distinguish Home, swiping Recents, screen lock or opening another screen. Screen off does not prove Doze. Dynamic observers only work while the process exists, and timestamps describe when the app observed an event. Missing events during process death cannot be reconstructed. No always-running service, polling or extra wake lock is introduced. This release does not fix the reported unlock-dependent alarm delay.
