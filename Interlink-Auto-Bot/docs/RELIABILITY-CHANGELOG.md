# Wake-lock cycling experiment 1.8-rc4

- Adds optional, default-off 120-second wake-lock release/reacquisition for Scheduler's persistent and bounded execution services, with structured configuration/cycle/failure events and configuration in device snapshots.
- Reacquisition cannot extend the original wall/elapsed execution deadline. Cycle failure stops the service and records unknown execution outcome without retrying claims.
- Adds a ten-minute no-claim continuous survival probe to cover the previously observed later process termination. Tests release/reacquisition mechanics, not an undocumented OEM-counter reset.
- Available OEM settings are accepted as already enabled per the user; no repeated setup request. iManager UI activation is not a prerequisite for the system component recorded in exit descriptions.
- Does not disable system packages or add unrelated special-access services. No assertion of a fixed four-minute limit, kernel restriction, or guaranteed force-stop immunity.

# Backgrounding and public exit metadata candidate 1.8-rc3

- Uses moveTaskToBack(true) for Background app, keep scheduling. It preserves the UI task instead of removing it.
- Adds bounded redacted ApplicationExitInfo.description, numeric/named reason, status, importance and PSS/RSS. A new cursor reads retained historical records once after upgrade. Nullable descriptions and unavailable internal subreasons are explicit.
- Records the review's useful suggestions and corrects explicit-intent, PendingIntent identity and hidden-subreason assumptions in REVIEW-FOLLOWUP.md. No root cause or OEM-specific force-stop caller is asserted.

# Task removal and startup diagnosis candidate 1.8-rc2

- Explicitly declares stopWithTask=false (Android's previous default), records onTaskRemoved while retaining the active timer, and adds Close screen, keep scheduling. This is not a claim that a manifest flag bypasses OEM process termination.
- Adds an emulator test with actual Recents swipe and subsequent screen-off timer, separately gated from routine instrumentation.
- Android-provided Termux bootstrap performs offline checks and only forwards safe correlated reports. Outdated bot source is identified before execution; missing report after exit 2 gets an explicit safe fallback reason. No raw stdout/stderr is imported.
- Scheduled wrapper protocol 2 adds startup phase and safe fallback reason. Both source and APK must be updated. Tests use temporary fake installations and never access real credentials or submit claims.
- Historical exit 2 remains unexplained without its private Node log; new reports diagnose future startup failures rather than inventing a past cause.

# Optional continuous scheduler candidate 1.8-rc1

- Adds a two-minute no-claim test of the same in-process timer; it records planned/fired events and delay and posts a result notification.
- Adds a user-controlled Android foreground service with persistent notification and partial CPU wake lock. A planned in-process timer triggers the existing scheduled claim flow at the original daily start times. Existing exact alarms remain registered and duplicate/ledger guards remain authoritative.
- Service can be stopped in app or from the notification; pausing schedule stops it. Requested versus actual running status is visible. Auto-restart is attempted after reboot/update and when opening the app, but OEM and force-stop limits remain.
- Significant battery cost is intentional for this mode; it is off by default. No continuous Node/crond process.
- Dedicated `specialUse` foreground-service declaration avoids the Android 15 dataSync six-hour limit for this scheduler use case. Distribution through Google Play would require service-use review. Physical device success and overnight battery impact remain unverified.

# Alarm-clock comparison candidate 1.7-rc4

- Added one explicit ten-minute, user-visible `setAlarmClock()` connection probe, with delivery notification, cancellation and separate method identification in the existing exported log.
- Uses the same single-probe identity, so new probes replace old ones. Six daily claims and Node behavior remain unchanged. No claims submitted by the probe.
- Added emulator verification for registration/cancellation without changing daily slots, receiver delivery without UI opening, and opt-in forced-idle delivery.
- Physical evidence so far: standard probe delayed 28.993s after process death and 98.229s with the same process alive. The latter arrived while non-interactive, before screen-on. Neither proves a specific OEM cause or that unlock is always required.
- This comparison has not yet been run on the affected phone and is not a reliability fix claim.

# Live debug candidate 1.7-rc3

- Added persistent, default-off Live debug logging switch, app visibility events, observed screen/unlock/power events and an on-demand system-state snapshot.
- All event snapshots now include device/keyguard lock state, model/manufacturer and debug-enabled state. Core execution evidence remains enabled regardless of the switch.
- Observers exist only while the process lives; no new service, polling or wake lock. Background events do not distinguish Home from swipe or lock. No assertion that an unobserved transition never happened.
- Retains Downloads text-log export. Scheduling and Node logic unchanged; unlock-dependent delivery is unresolved.

# Diagnostic export candidate 1.7-rc2

- Added **Save diagnostic log to Downloads**: one UTF-8 text file with companion activity, current device state, scheduled slots, outcome summaries and structured events. Android 10+ uses MediaStore Downloads without storage permission; Android 8/9 uses the system save picker.
- Export runs off the UI thread. Pending Downloads files are published only after writing completes; failed writes attempt cleanup and show an error. Exports have unique filenames and are not deleted by automatic log retention.
- No raw Termux files or credentials are read. Exported files contain diagnostic device/timing information and should be shared intentionally.
- User reports delayed alarm reception and Termux notification on unlock without opening Scheduler. This is distinct from app-open recovery. No claim that this export change fixes alarm delivery or identifies the underlying OS cause.
- Scheduling and Node claim behavior remain unchanged from 1.7-rc1. Prior physical-validation blockers remain open.

# Reliability candidate 1.7-rc1 — changes and limits

- Added source-audited root-cause matrix and architecture decision. Root cause of the physical phone's absent receiver evidence remains unknown.
- Added private SQLite events and per-run outcome summaries; logical execution ID is `schedule-<original epoch ms>`, with UUID attempt IDs for callbacks. The Node per-local-window ledger still guards claims (including repeated local hours).
- Receiver entry is captured before validation; rejected disabled/stale/duplicate/invalid intents get explicit reasons. Registration, permission checks, dispatch and missing-delivery reconciliation use structured events.
- Automatic foreground reconciliation preserves future slots; explicit repair and system rebuild still intentionally re-register them and record the action. Recovery retains original deadlines and never treats recovery as actual alarm delivery.
- Foreground-promotion errors, Termux internal error codes, dispatch failures and missing callbacks are distinguished. No raw Termux error text is imported because it may include sensitive paths/output.
- Scheduled wrapper keeps private logs separate from callback stdout. Only a bounded, allowlisted JSON report returns to the companion. Correlation mismatch, missing/old wrapper output or malformed report yields UNKNOWN, not success.
- Node records start/exit, token refresh, eligibility, claim submission and each independent window outcome. Final reasons distinguish timing, ineligibility, authentication, request stage, verification and uncertain submission. Detailed safe error codes/HTTP statuses remain in structured events.
- Preserved token persistence, legitimate API workflow, claim schedule, shared flock, submitted-before-POST ledger and conservative no-retry treatment of uncertain submission. No automatic reconciliation can attribute an ambiguous claim to this client solely from another advancing server timestamp; uncertain records remain blocked rather than guessed successful.
- Added device state snapshots (SDK, saver, charging, idle, standby bucket, network, exact capability, process and elapsed time), dynamic observable power transitions and bounded process-exit metadata. No continuous monitoring service; unknown historical power state remains unknown.
- Added monitoring card, claim-window summaries, receiver-delivery statistics, JSONL clipboard export and 4h/8h no-claim probes. Counters start with this version and exclude cancelled due slots; imported historical free text is not counted as proven delivery.
- Manifest adds ACCESS_NETWORK_STATE and Application observer only. No Accessibility, overlay, device-admin, special privileged logging, persistent cron or always-running Node process.
- WorkManager and native migration were evaluated, not added speculatively. See the audit for why neither fixes missing OS alarm delivery. Always-on Linux remains the more dependable alternative if exact phone execution fails acceptance.

This is not a permanent-fix or production-readiness claim. Physical T4/T8 and multiple daily intervals are release blockers until evidence exists.
