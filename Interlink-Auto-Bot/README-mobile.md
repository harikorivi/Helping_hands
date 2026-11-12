# Interlink Scheduler — first-time Android installation

This guide installs **version 2.0-rc2 (continuous-service candidate)** for a new user. **Not production-ready: physical-device four-hour and overnight acceptance is pending.** Read the [service architecture decision](docs/ADR-SERVICE-ONLY.md) and [release validation](android-companion/releases/VALIDATION-2.0-rc2.md). Everything needed is in this repository: the Node.js bot, authentication and eligibility handling, on-demand actions, and the Android companion app. Follow the steps in order; no patch ZIPs or text editor are needed.

The system has two components on your phone:

- **Termux** runs the Node.js bot and stores your credentials privately.
- **Interlink Scheduler** is the companion APK with a teal clock icon. It schedules execution and provides **Test connection — no claim** and **Submit one eligible claim now** buttons. It is not the official Interlink app.

Interlink Scheduler runs one continuous foreground service with a persistent notification and CPU wake lock. This intentionally increases battery use. You do not need cron, Termux:Boot, Termux:API, a continuously running Node process, or a connected Mac after installation. Android background execution remains subject to battery restrictions and phone manufacturer policies. Do not force-stop either app.

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

On your phone, open [Interlink-Scheduler-2.0-rc2.apk](https://github.com/harikorivi/Helping_hands/raw/refs/heads/main/Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-2.0-rc2.apk) and download it. The same file is in the repository at:

```text
Interlink-Auto-Bot/android-companion/releases/Interlink-Scheduler-2.0-rc2.apk
```

Open the downloaded APK using your browser or Files app. If Android asks, allow **Install unknown apps** for the browser/Files app you are using, then select **Install**. You can turn that installation permission off afterward.

Find **Interlink Scheduler** by its teal clock/check-mark icon and open it. This is a personal debug-signed APK, not a Play Store release. No Mac build is needed to use the supplied APK.

## 6. Grant access to the named apps

Open **Interlink Scheduler** and use the **Setup** buttons. Tap once, then return from Android Settings.

| Button / setting | App to configure | Required action |
|---|---|---|
| Grant Termux command permission | Interlink Scheduler | Allow **Run commands in Termux environment**. If Android suppresses the prompt, open Scheduler App info → Permissions / Additional permissions. |
| Allow scheduler notifications | Interlink Scheduler | Allow notifications so the continuously running service is visible. |
| Request Scheduler battery exemption | Interlink Scheduler | Accept Android’s native exemption dialog, then return and verify **Scheduler=true** in Android battery exemptions. A request alone is not a grant. |
| Scheduler app / battery settings | Interlink Scheduler | Allow unrestricted background power use / background activity, using the choices offered by the phone. |
| Termux app / battery settings | Termux | Allow unrestricted background power use / background activity. |

Keep **Battery Saver off** during the continuous-service survival test. Termux needs its own exemption/background settings; the Scheduler dialog cannot grant access on Termux’s behalf. If the phone does not offer the native dialog, use **Open battery optimization list**, select all apps, and configure Scheduler there.

This version does **not** use AlarmManager or require **Alarms & reminders** access. Android battery exemption does not prove that an OEM will preserve the process. The affected vivo/iQOO device has recorded OEM-requested force-stops despite allowed Android settings. This release changes application structure; it does not claim to override those stops. Do not repeatedly change already-enabled permissions as a substitute for inspecting execution evidence.

## 7. Test the installation immediately

In **Interlink Scheduler**, tap **Test connection — no claim**.

Wait for the result in the service status area, allowing up to two minutes. The test starts Termux, validates/refreshes authentication, and reads account and eligibility information. It does **not** submit a claim. A successful test confirms the foreground execution path; it does not yet prove service survival while the phone is locked.

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

Tap **Start / repair 24-hour scheduling**. Confirm **Schedule: enabled**, **Service: running**, **CPU wake lock: true**, and the expected **Next** local time. The notification is titled **Interlink Scheduler service**. Keep it running; closing the activity does not pause the schedule.

| Run starts | Check the log after |
|---|---|
| 01:26 AM | 01:35 AM |
| 05:26 AM | 05:35 AM |
| 09:26 AM | 09:35 AM |
| 01:26 PM | 01:35 PM |
| 05:26 PM | 05:35 PM |
| 09:26 PM | 09:35 PM |

Each run has two independent windows: **:26:00 through :29:59**, and **:31:00 through :33:59**. It attempts at most one submission per window, only if the server reports eligibility. Node exits after the windows. The single companion foreground service and its CPU wake lock stay active between sessions; Node may sleep between the two windows within one session. The six times are local clock times, not four-hour delays chained from completion.

You may press **Home** or swipe the Scheduler screen from Recents; its service declares `stopWithTask=false`. Android's memory-pressure restart is best effort. A manufacturer may still terminate or force-stop it. **Do not Force stop Termux or Interlink Scheduler.** Reopen Termux and Scheduler, then tap **Start / repair 24-hour scheduling** after a true force-stop. Reboot recovery starts the enabled service after normal unlock/credential-encrypted storage availability, subject to platform restrictions.

Before the next real session, choose **2-minute service timer test — no claim**. Swipe Scheduler from Recents, lock the phone and leave it untouched for three minutes. On return, inspect its timer status or export diagnostics. A successful timer test proves only the companion timer, not the complete Termux/API path. The **10-minute** test checks longer survival. These probes do not launch Termux, but the normal six scheduled sessions remain enabled.

Pausing with **Pause scheduled runs** or **Pause schedule** in the notification stops future sessions and releases the service/wake lock once any active execution completes or its original deadline expires. A missing callback is recorded as unknown; the same session is not resubmitted after a restart.

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

The scheduled maximum is 12 successful claims per day, subject to eligibility and actual execution. The `logs` directory and today's `claim-YYYY-MM-DD.log` are created when the scheduled wrapper actually starts. A service timer probe or manual connection test does not create that claim log. Repairing the plan does not create an empty success log.

If today's claim file is missing, export diagnostics and trace **SERVICE_TIMER_FIRED → EXECUTION_DISPATCH_REQUESTED → TERMUX_DISPATCHED → NODE_PROCESS_STARTED → both CLAIM_WINDOW_*_COMPLETED → EXECUTION_COMPLETED/FAILED**. A dispatch is not proof that Node started or that a claim succeeded. Missing evidence remains unknown. Startup reasons such as `bot_update_required`, `dependencies_missing` and `wrapper_exit_without_report` appear in the app report; two window results are shown independently.

## Optional: submit one real claim now

Tap **Submit one eligible claim now** and read the confirmation dialog. Confirm only if you intend to submit a real claim. The action checks eligibility, submits at most once and verifies the result, without waiting for a second window. It can affect eligibility for later scheduled runs.

Manual claims have a one-minute cooldown and share the bot's execution lock. An uncertain submitted manual claim blocks further manual claims pending review of the actual server outcome. Do not delete `.runtime/manual.json` to bypass that protection. Separate manual claims are not included in the scheduled maximum of 12.

For terminal use, `bash run-on-demand.sh test` performs the connection test. `bash run-on-demand.sh claim` requests a real claim without a confirmation prompt; use the app's confirmed button when learning the system.

## Automatic log cleanup

The bot keeps **today's and yesterday's logs**, using your phone's local calendar dates—not a rolling 48-hour interval. For example, on 8 November, logs dated 8 and 7 November remain; recognized daily logs dated 6 November or earlier are deleted.

- **Termux:** at the start of each scheduled or on-demand execution, under the shared execution lock, cleanup removes older `logs/claim-YYYY-MM-DD.log` and `logs/on-demand-YYYY-MM-DD.log` files. It does not scan other directories or follow symlinks. A cleanup error is logged without blocking the claim/test execution.
- **Companion:** old activity entries are removed when the app opens (even while scheduling is paused) and whenever a new activity event is recorded. UTC event timestamps are converted to the phone's local date. The existing limit of 100 stored entries still applies, so very busy days may retain fewer than two complete days. The service prunes structured diagnostics hourly; exports retain at most 4,000 structured events and 30 days of run summaries.

Credentials, `.runtime` claim records, execution locks, scheduling preferences, and the latest action status are not deleted. Unknown filenames/formats and future-dated entries are preserved conservatively; app history still has its 100-entry cap. Future dates can occur after a clock correction. Cleanup runs at app opening, during service maintenance and at the next wrapper execution.

Log cleanup never deletes the private execution ledger or credential files.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Permission button does not show another prompt | It may already be allowed, or Android may suppress repeated prompts. Open Scheduler App info → Permissions. Tap once; double-tapping is unnecessary. |
| Token refresh fails | Check connectivity and the log. If reauthentication is required, pause scheduling, wait for the active run to finish, then run `node index.js --login`. |
| Unexpected eligibility response | The current code accepts HTTP 2xx with an explicit nested eligibility boolean; missing application status is allowed, but explicit errors/malformed fields are rejected. Share the safe `TEST_INVALID_RESPONSE` field-type diagnostics, not raw account data. |
| `missed_window` | The run started after that window closed. A manually started late run does not prove an Android alarm delay. |
| No scheduled log after minute 35 | Check whether the service heartbeat continued through the due time. Export diagnostics and trace the stage sequence above; check the fixed checkout path and Node startup reason. |
| Command/file missing | Confirm `~/Helping_hands/Interlink-Auto-Bot/run-on-demand.sh` exists and run setup again. Use the APK and source from this same repository version. |
| Another run is active | Wait for it to finish. Do not remove the lock file to force overlap. |
| Service stops or notification disappears | Reopen Scheduler and tap **Start / repair 24-hour scheduling**. Check retained process-exit descriptions; no guaranteed force-stop recovery is claimed. |

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

3. Install **Interlink-Scheduler-2.0-rc2.apk** over the old app using **Update**, with the same signing key. Do not uninstall first. If updating from 2.0-rc1, request the native battery exemption once and verify its returned status. This update adds guarded recovery for a callback-only service creation; it does not repeatedly restart a healthy service or override a true force-stop.

   The single service replaces old alarm/monitor modes and cancels their pending alarms once. Credentials, Node ledger and retained diagnostic history remain. Previous timer probes/cycling settings are retired. An enabled schedule restarts on app update/open, so pause before updating if you do not want scheduled work to resume yet.

4. Tap **Test connection — no claim**, then **Start / repair 24-hour scheduling**. Confirm the service/wake-lock status and notification. Only reauthenticate if stored credentials cannot refresh.
5. Source from 1.8-rc2 or later already provides wrapper protocol 2; no new Node authentication workflow is required. Updating source and APK together is the supported installation path.

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

Enable **Live screen, unlock and power-state logging** during testing to record activity foreground/background and observed screen/power broadcasts. Core scheduling/execution events, heartbeat and retained process-exit descriptions are always recorded. Device state is sampled asynchronously: `actualAt` is the event time, `deviceSampledAt` is the later state-sampling time, and `deviceAtImport` is callback-time state for imported Node events. A killed process cannot log the interval when it was absent.

See the [12 November task-removal evidence review](docs/TASK-REMOVAL-REVIEW-2026-11-12.md) for what the export establishes and what remains unknown.
