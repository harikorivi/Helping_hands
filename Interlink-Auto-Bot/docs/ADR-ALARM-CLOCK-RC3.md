# ADR: Six alarm-clock schedules and bounded executions — 2.0-rc3

12 November 2026. Status: implemented; physical iQOO 7 acceptance pending. This user-approved decision supersedes the continuous-service mode in rc1/rc2.

## Decision and alternatives

Use six independent, user-visible `AlarmManager.setAlarmClock()` registrations at local 01:26, 05:26, 09:26, 13:26, 17:26 and 21:26. Each receiver validates its persisted timestamp/generation, enabled state, original deadline and duplicate marker, reserves one attempt, starts the bounded `RunService`, and rearms that hour's next local-calendar occurrence. There is no resident scheduling service, polling loop or wake lock between executions. Android may retain a cached process; the app does not force its process to exit.

This replaces continuous CPU wakefulness, reducing its between-slot battery cost. WorkManager remains unsuitable for the minute-sensitive windows. The current native client is retained in Termux to preserve its tested authentication/claim sequence, at the cost of a second package's lifecycle and IPC boundary. A fully native API rewrite remains deferred. No OEM force-stop immunity is claimed.

Alarm-clock events are user-visible and may appear in Android's system next-alarm display. They can provide a documented background foreground-service start exemption for a user-requested action. This does not prove exact-second delivery on Funtouch OS. No full-screen activity, sound, overlay, notification listener or accessibility service is added. [AlarmManager contract](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)), [foreground start exemptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Ownership and failure handling

- `AlarmSchedule.kt`: six durable slot/generation pairs, distinct PendingIntent identities, pause/cancel, next-calendar-day calculation, permission checks, upgrade migration and no-claim alarm probes. A registration is evidence of an accepted API call, not proof of receiver delivery.
- `SlotAlarmReceiver`: captures receiver wall/elapsed time and a fresh attempt ID, rejects stale/canceled/early/expired/duplicate payloads, and atomically persists the accepted marker and bounded reservation before dispatch. The next daily slot is rearmed independently, including after launch rejection.
- `Handoff`: a per-attempt partial wake lock, timed to the earlier of 15 seconds or the original remaining deadline. `RunService` acquires its own timed lock before releasing the handoff. If startup never happens, the handoff expires automatically. It does not hold the receiver's lock indefinitely or release it immediately after the asynchronous start request.
- `RunService`: one bounded `dataSync` foreground service. Promotion happens before preference/database/device sampling. A scheduled execution ends at its original :34 deadline; a manual/test/probe session has at most two minutes. The lock is timed; foreground notification, lock, executor and HandlerThread are released after matching completion or deadline. `START_NOT_STICKY` prevents a killed attempt from being re-dispatched on automatic service recreation. Callback-only task removal does not resurrect a resident scheduler.
- `SessionStore`: shared Android reservation, original wall/elapsed deadlines, boot identity and up to 40 correlated attempt records. Expired prior executions become unknown without resubmission. Node's shell flock and persistent pre-POST submission ledger remain the authoritative duplicate barriers.
- `ResultReceiver`: accepts only known attempt metadata, validates the structured Node report, records independent window outcomes and safe stage details, and stops only the matching execution. Missing/malformed reports never imply successful claims. Late callbacks can improve an unknown outcome but cannot stop a newer execution.
- `RecoveryReceiver`: boot, update, clock/timezone and exact-alarm grant recovery re-register future alarms without starting a dataSync service from boot. No settings activity is launched from a receiver. Credential-encrypted storage and Termux become available after normal first unlock; pre-unlock claim execution is deliberately unsupported. [Direct Boot storage](https://developer.android.com/privacy-and-security/direct-boot).

Actual late alarm delivery may use remaining time within `[26:00,30:00)` and `[31:00,34:00)`. Reconciliation on app-open/repair preserves an already-armed original slot through its valid window; after expiry it records missing delivery and plans future work. It never fabricates delivery or starts an app-open catch-up claim. rc4 corrects the premature replacement described in the [physical field review](RC3-FIELD-REVIEW-2026-11-12.md). Clock/timezone changes recalculate future calendar times. Existing active Node executions retain their original deadlines and clock-change safeguards. Fixed 24-hour arithmetic is not used.

## Permissions and setup

Use `SCHEDULE_EXACT_ALARM`, not restricted `USE_EXACT_ALARM`; verify capability through `canScheduleExactAlarms()`. Capability may also derive from battery exemption. Preserve explicit user-triggered native battery-exemption requests, Termux command access, notifications and separate Termux background settings. A user may enable alarms with a battery warning, but the physical acceptance campaign requires both apps' intended power access to be confirmed. Permission denial/revocation is exposed as missing capability; it does not create a restart loop.

Optional vivo/iQOO shortcuts resolve exported candidate activities and catch failures, falling back to this app's App Info. They neither grant nor verify private OEM access. No sensitive accessibility/notification-listener permissions or system-package disabling is introduced.

## Evidence and trial retention

The event chain is `ALARM_SCHEDULED → ALARM_RECEIVED → EXECUTION_DISPATCH_REQUESTED → FOREGROUND_PROMOTED → TERMUX_DISPATCHED → NODE_PROCESS_STARTED → both CLAIM_WINDOW_*_COMPLETED → NODE_PROCESS_EXITED → EXECUTION_COMPLETED/FAILED → EXECUTION_RESOURCES_RELEASED`. A no-claim probe ends after foreground promotion and does not expect Node/window events.

Execution IDs identify original scheduled timestamps; attempt IDs identify accepted dispatches; service IDs identify foreground instances. Receiver, service-entry, promotion and Node timestamps are separate. Top-level `pid` belongs to the event source; `observerPid` identifies Android importing Node events. Device sampling timestamps remain separate from event time. Historical exit timestamp/PID must be read from `ApplicationExitInfo` details, not from the later observer snapshot. No causal OEM rule is inferred from a numeric exit code alone.

A canceled summary is reset when the same future timestamp is registered again; confirmed historical outcomes are retained. Structured trial evidence defaults to three local days/6,000 rows so a 48-hour test spanning three dates is reviewable. Turning off **48-hour trial evidence** returns to today/yesterday and 4,000 rows. Human activity remains capped at 100 entries/two local dates; Termux daily-log cleanup remains unchanged. No credentials or claim ledger are pruned.

## Release gate

See the [acceptance checklist](RC3-ACCEPTANCE.md) and [executed local validation](../android-companion/releases/VALIDATION-2.0-rc3.md). Emulator tests cannot certify vivo policy, physical reboot recovery, server eligibility or an unattended overnight campaign. True force-stop can cancel delivery mechanisms and requires user interaction; reboot/alarms are not a guaranteed escape from the stopped state.
