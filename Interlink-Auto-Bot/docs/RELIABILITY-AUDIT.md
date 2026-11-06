# Unattended execution audit — 6 November 2026

Status: investigation open. v1.6 missed the reported 17:26 run. The evidence does not establish a permanent device-specific fix. This document was written before implementing the next candidate.

## Established evidence

The vivo I2012/Android 14 report records registration at 14:35:20 for 17:26, and foreground overdue reconciliation at 18:03:46. It contains neither receiver evidence nor Termux dispatch evidence for that slot. Battery Saver was true at report collection, not necessarily at 17:26. The separate diagnostic app received alarms 32s and 72s late with interactive=false, before reopening. This disproves a blanket claim that the device never delivers alarms to a dismissed app, but does not prove long-idle scheduler delivery. Another Android 15 phone delivered scheduled alarms late and returned exit 2: that is a different failure stage.

## Root-cause matrix

| Hypothesis | Supporting evidence | Contrary evidence / limits | Missing evidence | Reproduction | Correction / decision |
|---|---|---|---|---|---|
| Doze deferred alarm | Locked/idle usage; delayed diagnostic alarms | idle=false at sampled diagnostic deliveries; allow-while-idle is used | State at intended time; system alarm history | Forced-Doze emulator test, then 4h locked-device test | Retain supported allow-while-idle; capture per-event idle state; no guarantee against quotas |
| Battery Saver | saver=true at 18:03 report | State at 17:26 unknown | Observable power transitions before/after deadline | Paired long-idle tests saver on/off | Log state while process alive and at every event; do not infer history from final snapshot |
| Restricted standby bucket | Long inactivity/OEM policies plausible | No bucket captured in v1.6 | UsageStatsManager.appStandbyBucket samples | Controlled restricted-bucket emulator test | Report bucket, reject claims after deadline; no stealth exemption |
| Exact access lost | Revocation can remove scheduled alarms | Capability true at collection; no proof at deadline | Permission/capability transitions and registration exceptions | Revoke exact access on emulator after scheduling | Log permission check per registration; re-register after grant; distinguish capability from grant |
| PendingIntent replaced/cancelled | v1.6 repairs all six with UPDATE_CURRENT, cancelling legacy intents; shared hour identities | No recorded repair between 14:35 and 18:03 | All replacements/cancels with expected timestamps | Duplicate/repair race and timezone tests | Preserve upcoming slots during ordinary reconciliation; log explicit replacements/cancellation; correlate by scheduled instant |
| Receiver misregistered/disabled | No entry record for missed slot | Manifest explicit non-exported receiver works in tests | Component state, receiver entry before guards | Invoke disabled/duplicate/stale payload paths | Record RECEIVER_STARTED before preference guards, then explicit rejection reason |
| Process killed/force-stop | User dismisses Recent Apps | Dismissal is not proof of force-stop; diagnostic app can receive cold | App exit history and OEM force-stop evidence | am kill vs am force-stop vs task swipe | Record process/exit metadata without stack dumps; do not claim force-stop recovery |
| OEM battery policy | Model-specific delays despite exemptions | No privileged OS trace; delayed delivery also possible under standard policies | Funtouch-specific system trace | Same configuration, long-idle comparisons on physical phones | Remains unresolved; no extra accessibility/overlay permissions or disabled security controls |
| Receiver ran but failed before log | v1.6 logs after action/enabled/payload/duplicate guards and synchronous preference writes | No exception evidence | First-entry durable event, process exits, storage failures | Invalid payload, disabled schedule, persistence and dispatch tests | SQLite event ledger; first-entry diagnostics; fallback storage error indication; absence still not proof of no invocation |
| Wrong scheduling API | Minute-sensitive deadlines | RTC_WAKEUP + setExactAndAllowWhileIdle is supported; WorkManager is inexact | OS queue at registration/deadline (unavailable without privileged diagnostics) | Verify actual OS delivery and six-slot identity | Keep API. Do not substitute setAlarmClock merely to evade power policy; this is not an alarm-clock UI |

## v1.6 chain audit and confirmed weaknesses

1. Per-hour request codes distinguish six alarms; PendingIntent extras do not define identity. UPDATE_CURRENT replaces the timestamp for a slot. Ordinary recovery unnecessarily re-registers all future alarms. A registration success is not evidence of future delivery.
2. Receiver logs occur after several guards; silent returns hide invoked-but-rejected events. Persisting delivered before service startup and rearming immediately hides startup failure behind a delivered flag.
3. Bounded dataSync foreground service is appropriate only at authorized triggers (user activity/exact alarm exemptions). Boot/timezone recovery must not directly start a catch-up dataSync service, particularly on Android 15/target 35. Foreground promotion itself can throw and must be diagnosed.
4. Companion wake lock is bounded by original run deadline. Shell timeout bounds the process but a frozen OS cannot execute a cleanup callback until resumed. Node's suspend-aware wall deadline remains essential.
5. RUN_COMMAND and callback PendingIntent are supported Termux mechanisms. Command permission, external-apps setting, installation/version, background dispatch restrictions and callback loss are distinct failure points. startService success means dispatch requested, not Node started.
6. v1.6 redirects all stdout to a file. Android receives only exit status and cannot display either claim window's reason. A generic exit 2 collapses ineligibility, auth, API, timing and uncertain submissions. Fix with a bounded allowlisted report, not raw stdout/account data.
7. Credentials are atomically persisted as a pair; unattended refresh failure does not prompt OTP. Preserve this logic. Add refresh lifecycle hooks, never token payloads.
8. Shell flock and persistent per-local-window ledger are the duplicate barriers. A submitted marker must remain durable before POST. Unknown server outcomes must not be retried blindly. Advancing timestamps alone after an ambiguous request cannot prove which caller claimed; retain uncertainty and no resubmission unless evidence is sufficient.
9. Scheduling history is a 100-entry human-readable preference string, with power only at selected points. It cannot yield reliable registered/delivered denominators or cross-process correlation. Introduce a structured durable event/run ledger and distinguish actual delivery from recovery.
10. UI opening is a reconciliation opportunity, not a background guarantee. Reconcile expired active attempts as unknown/interrupted, never as successful or proof of failed claims.

## Architecture decision

Retain six independent Android exact allow-while-idle alarms. No resident cron/Node process. Receiver: record entry, validate identity/state, record delivery, request bounded foreground service, rearm only its slot. Service: promote immediately, hold bounded wake lock, dispatch supported Termux command with logical execution ID and separate attempt ID. Node: preserve original two windows and claim ledger, emit safe lifecycle/summary; wrapper: return small report through Termux callback while detailed logs remain private. SQLite stores Android events, imported Node evidence and run outcomes; dashboard/export read that evidence.

Reconcile on opening, explicit repair, reboot, permission grant, clock/timezone change and package update. Ordinary repair preserves future identities; system lifecycle rebuilds are recorded. Foreground recovery can attempt remaining valid windows, using the same lock/ledger. Background reconciliation never blindly launches an FGS or expired claim. Missed detection occurs at the next authorized CPU opportunity, not magically while the app is stopped.

WorkManager: useful for eventual diagnostics/reconciliation, not three-minute eligibility windows. Periodic work has a 15-minute minimum and execution is subject to constraints/Doze; it cannot provide a timely fallback or unrestricted FGS launch. Do not add a periodic worker to imply reliability. It is deferred until real-device evidence shows a benefit beyond existing lifecycle recovery.

Native client migration removes Termux's cross-app startup and callback failure points, but cannot fix absent alarm delivery. Preserve current client until traces identify dispatch as the bottleneck. If strict wall-clock execution remains unreliable, the more dependable architecture is the same one-shot Node workflow on an always-on Linux host with systemd timers, an explicit Asia/Kolkata timezone, locked private credentials and the existing ledger. That avoids phone sleep/OEM policy but still depends on network/server eligibility and user acceptance of off-phone credentials. Do not deploy it without that choice.

No approach guarantees two claims: eligibility and confirmed server acceptance govern outcomes. No app guarantees execution after true force-stop, shutdown or revoked access.

## Primary references

- [Android alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Foreground service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android 15 dataSync restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed)
- [WorkManager timing](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Termux RUN_COMMAND and result callback](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
