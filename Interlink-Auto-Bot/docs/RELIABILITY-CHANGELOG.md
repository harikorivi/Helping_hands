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
