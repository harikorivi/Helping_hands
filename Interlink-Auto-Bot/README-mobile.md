# Interlink Scheduler — first-time Android installation

This guide installs **version 2.0-rc3 (alarm-clock candidate)** for a new user. **Not production-ready: physical-device four-hour and overnight acceptance is pending.** Read the [alarm-clock architecture decision](docs/ADR-ALARM-CLOCK-RC3.md) and [release validation](android-companion/releases/VALIDATION-2.0-rc3.md). Everything needed is in this repository: the Node.js bot, authentication and eligibility handling, on-demand actions, and the Android companion app. Follow the steps in order; no patch ZIPs or text editor are needed.

The system has two components on your phone:

- **Termux** runs the Node.js bot and stores your credentials privately.
- **Interlink Scheduler** is the companion APK with a teal clock icon. It schedules execution and provides **Test connection — no claim** and **Submit one eligible claim now** buttons. It is not the official Interlink app.

Interlink Scheduler registers six user-visible Android alarm clocks. No scheduling service or wake lock stays active between executions. Android may keep the app cached; its screen can be closed. You do not need cron, Termux:Boot, Termux:API, a continuously running Node process, or a connected Mac after installation. Android background execution remains subject to battery restrictions and phone manufacturer policies. Do not force-stop either app.

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

On your phone, open [Interlink-Scheduler-2.0-rc3.apk](https://github.com/harikorivi/Helping_hands/raw/refs/heads/main/Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-2.0-rc3.apk) and download it. The same file is in the repository at:

```text
Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-2.0-rc3.apk
```

Open the downloaded APK using your browser or Files app. If Android asks, allow **Install unknown apps** for the browser/Files app you are using, then select **Install**. You can turn that installation permission off afterward.

Find **Interlink Scheduler** by its teal clock/check-mark icon and open it. This is a personal debug-signed APK, not a Play Store release. No Mac build is needed to use the supplied APK.

## 6. Grant access to the named apps

Open **Interlink Scheduler → Setup**. Tap once, approve the native dialog if offered, then return.

| Control | App | What to verify |
|---|---|---|
| Grant Termux command permission | Scheduler | Allow **Run commands in Termux environment**. |
| Allow scheduler notifications | Scheduler | Allow active execution and probe notifications. |
| Alarms & reminders access | Scheduler | Allow access on Android 12+, and verify **Exact-alarm capability: true** on return. Older Android needs no special permission. Capability can also derive from battery exemption. |
| Request Scheduler battery exemption | Scheduler | Approve Android’s native dialog and verify **Scheduler=true**. Opening the dialog alone is not a grant. |
| Termux app / battery settings | Termux | Allow background power use and its intended battery exemption separately. |
| vivo / iQOO shortcuts, if shown | Scheduler | Best-effort navigation to Autostart/Background Power. Unsupported screens fall back to App info; the app cannot verify private OEM toggles. |

Keep Battery Saver off for the unattended trial. The app reports the observed Android state; this is not a promise of OEM process survival. No accessibility, notification-listener or overlay permission is required. `setAlarmClock()` entries may appear in the phone’s system next-alarm display; this is expected user-visible scheduling, not a silent hidden alarm.

## 7. Test the installation immediately

In **Interlink Scheduler**, tap **Test connection — no claim**.

Wait for the result in the service status area, allowing up to two minutes. The test starts Termux, validates/refreshes authentication, and reads account and eligibility information. It does **not** submit a claim. A successful test confirms the foreground execution path; it does not yet prove alarm delivery and bounded execution while the phone is locked.

If the test fails, inspect the private log in Termux:

```sh
cd ~/Helping_hands/Interlink-Auto-Bot
```

```sh
tail -n 40 "logs/on-demand-$(date +%Y-%m-%d).log"
```

Logs report safe metadata such as request stage, HTTP status and field types. Do not share raw API response bodies or credential files.

> Manual actions reserve **:24 through :33** during hours **01, 05, 09, 13, 17 and 21** for automatic execution, even when the schedule is paused. If the app asks you to defer, try after minute 34. An existing run also prevents overlapping execution.

## 8. Enable the six daily alarms

Tap **Enable / repair six alarms**. Confirm **Schedule requested: enabled** and **Accepted registrations: 6/6**, exact-alarm capability, six accepted registrations and the next local time. **Execution service: idle** and **wake lock: false** are correct between sessions; a persistent scheduling notification is no longer expected.

| Start | Inspect results after |
|---|---|
| 01:26 AM | 01:35 AM |
| 05:26 AM | 05:35 AM |
| 09:26 AM | 09:35 AM |
| 01:26 PM | 01:35 PM |
| 05:26 PM | 05:35 PM |
| 09:26 PM | 09:35 PM |

Each session retains original windows **:26:00–:29:59** and **:31:00–:33:59**, ending at :34. Late receiver delivery can use only remaining time. The next occurrence is calculated from the local calendar, including timezone/DST changes. Missed slots found during app-open/repair are recorded without app-open claim replay.

Use **2-minute alarm-clock test — no claim** first. Lock the screen and leave the phone untouched for three minutes. The probe measures Android receiver delivery and bounded foreground startup, then releases resources. It launches no Node or API claim, and can be run independently of the daily schedule. The **10-minute** probe supports a longer trial; normal scheduled work remains enabled if you enabled it.

Pressing Home or normally dismissing the UI does not require a resident service. During an actual execution, the foreground service declares `stopWithTask=false`. Some OEMs may still kill or force-stop the package. Do not promise delivery after a true force-stop: reopen Termux/Scheduler and repair afterward. Boot/update/clock-change recovery restores future alarms after normal first unlock; it does not access credentials before unlock.

**Pause scheduled runs** cancels future alarms and probes while allowing an active bounded execution to finish within its original deadline. No uncertain submission is blindly retried after a process restart.

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

The scheduled maximum is 12 successful claims per day, subject to eligibility and actual execution. The `logs` directory and today's `claim-YYYY-MM-DD.log` are created when the scheduled wrapper actually starts. An alarm-clock probe or manual connection test does not create that claim log. Repairing the plan does not create an empty success log.

If today's claim file is missing, export diagnostics and trace **ALARM_RECEIVED → EXECUTION_DISPATCH_REQUESTED → TERMUX_DISPATCHED → NODE_PROCESS_STARTED → both CLAIM_WINDOW_*_COMPLETED → EXECUTION_COMPLETED/FAILED**. A dispatch is not proof that Node started or that a claim succeeded. Missing evidence remains unknown. Startup reasons such as `bot_update_required`, `dependencies_missing` and `wrapper_exit_without_report` appear in the app report; two window results are shown independently.

## Optional: submit one real claim now

Tap **Submit one eligible claim now** and read the confirmation dialog. Confirm only if you intend to submit a real claim. The action checks eligibility, submits at most once and verifies the result, without waiting for a second window. It can affect eligibility for later scheduled runs.

Manual claims have a one-minute cooldown and share the bot's execution lock. An uncertain submitted manual claim blocks further manual claims pending review of the actual server outcome. Do not delete `.runtime/manual.json` to bypass that protection. Separate manual claims are not included in the scheduled maximum of 12.

For terminal use, `bash run-on-demand.sh test` performs the connection test. `bash run-on-demand.sh claim` requests a real claim without a confirmation prompt; use the app's confirmed button when learning the system.

## Automatic log cleanup

The bot keeps **today's and yesterday's logs**, using your phone's local calendar dates—not a rolling 48-hour interval. For example, on 8 November, logs dated 8 and 7 November remain; recognized daily logs dated 6 November or earlier are deleted.

- **Termux:** at the start of each scheduled or on-demand execution, under the shared execution lock, cleanup removes older `logs/claim-YYYY-MM-DD.log` and `logs/on-demand-YYYY-MM-DD.log` files. It does not scan other directories or follow symlinks. A cleanup error is logged without blocking the claim/test execution.
- **Companion:** old activity entries are removed when the app opens (even while scheduling is paused) and whenever a new activity event is recorded. UTC event timestamps are converted to the phone's local date. The existing limit of 100 stored entries still applies, so very busy days may retain fewer than two complete days. Structured diagnostics are pruned on recovery/app opening. For this validation candidate, **48-hour trial evidence** defaults on: three local dates and up to 6,000 rows, covering a trial spanning three dates. Turning it off restores today/yesterday and 4,000 rows. Run summaries retain 30 days.

Credentials, `.runtime` claim records, execution locks, scheduling preferences, and the latest action status are not deleted. Unknown filenames/formats and future-dated entries are preserved conservatively; app history still has its 100-entry cap. Future dates can occur after a clock correction. Cleanup runs at app opening, during alarm recovery and at the next wrapper execution.

Log cleanup never deletes the private execution ledger or credential files.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Permission button does not show another prompt | It may already be allowed, or Android may suppress repeated prompts. Open Scheduler App info → Permissions. Tap once; double-tapping is unnecessary. |
| Token refresh fails | Check connectivity and the log. If reauthentication is required, pause scheduling, wait for the active run to finish, then run `node index.js --login`. |
| Unexpected eligibility response | The current code accepts HTTP 2xx with an explicit nested eligibility boolean; missing application status is allowed, but explicit errors/malformed fields are rejected. Share the safe `TEST_INVALID_RESPONSE` field-type diagnostics, not raw account data. |
| `missed_window` | The run started after that window closed. A manually started late run does not prove an Android alarm delay. |
| No scheduled log after minute 35 | Check intended/actual receiver and service timestamps. Export diagnostics and trace the stage sequence above; check the fixed checkout path and Node startup reason. |
| Command/file missing | Confirm `~/Helping_hands/Interlink-Auto-Bot/run-on-demand.sh` exists and run setup again. Use the APK and source from this same repository version. |
| Another run is active | Wait for it to finish. Do not remove the lock file to force overlap. |
| No service notification between sessions | Idle is expected. During a due execution, inspect alarm delivery and foreground-start evidence. A true force-stop still requires user interaction. |

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

3. Install **Interlink-Scheduler-2.0-rc3.apk** over the existing app with **Update** and the same signing key; do not uninstall. This migrates away from rc1/rc2 continuous scheduling, cancels known legacy alarms and retains credentials, claim ledgers, accepted markers and diagnostic history. Pause and let any active execution finish before upgrading.
4. Verify native battery exemption and exact-alarm capability, then tap **Enable / repair six alarms**. An enabled schedule is also restored on update/open when allowed. The idle service state is expected; use an alarm-clock probe to test delivery.
5. Node source from 1.8-rc2 or later already supplies wrapper protocol 2. No new login is required if token refresh works. Updating source and APK together remains the supported installation path.

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

The output is `app/build/outputs/apk/debug/app-debug.apk`. Verify its signature with the SDK's `apksigner` before distributing it. Keep a consistent private signing key for updates; never commit that key. The published personal build is debug-signed. Android UI and settings navigation have been inspected in an emulator; physical vivo overnight survival and real two-window eligibility still require device acceptance. Emulator success does not certify Funtouch OS behavior.

See [ANDROID-ARCHITECTURE.md](ANDROID-ARCHITECTURE.md) for the architecture review, security model, file responsibilities, alternatives, and testing matrix.


## Export evidence without USB or copying a long screen

Tap **Save diagnostic log to Downloads** and wait for the saved confirmation. On Android 10+, open **Files → Downloads → Interlink Scheduler** and share the newest `Interlink-diagnostics-*.txt` file. Android 8/9 opens a file picker. No storage permission is needed. The export contains companion metadata and imported safe Node results, never Termux credential files or raw API responses.

Enable **Live screen, unlock and power-state logging** during testing to record activity foreground/background and observed screen/power broadcasts. Core alarm/execution events and retained process-exit descriptions are always recorded. The bounded service may emit execution heartbeats, but there is no between-run heartbeat. Device state is sampled asynchronously: `actualAt` is the event time, `deviceSampledAt` is the later state-sampling time, and `deviceAtImport` is callback-time state for imported Node events. A killed process cannot log the interval when it was absent.


Follow [the rc3 48-hour acceptance checklist](docs/RC3-ACCEPTANCE.md). Local emulator checks do not certify physical vivo overnight reliability.
