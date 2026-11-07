# ADR: Android execution boundary and off-phone recommendation

Date: 2026-11-07 (Asia/Kolkata)
Status: Architecture review accepted; current Android release is not production-validated. No off-phone migration selected or deployed.
Method: senior-architect skill; requirements and failure-domain analysis, source inspection, explicit options/trade-offs and evidence-based decision. This is a review, not another APK release.

> Subsequent user decision: continuous background execution and its battery cost are now accepted. [Service-only ADR](ADR-SERVICE-ONLY.md) records the resulting implementation; this review preserves the preceding evidence and trade-offs.

## 1. Requirements and decision boundary

The Mac is the development/build machine. Android is the requested execution target. Six local-time sessions start at 01:26, 05:26, 09:26, 13:26, 17:26 and 21:26, each with independently eligible claims in the two original windows. Credential privacy, bounded requests, no blind retry of uncertain submissions and no claims after original deadlines remain hard constraints.

Avoiding resident cron/Node and minimizing battery are original preferences. UI dismissal should work whenever Android permits it. True force-stop recovery was explicitly excluded from the original guarantee. An off-phone host changes the deployment requirement, credential location and timezone responsibility; it is not a transparent implementation detail.

## 2. Evidence and what it proves

| Evidence | Supported conclusion | Unsupported conclusion |
|---|---|---|
| Public exit descriptions explicitly say FORCE STOP due to com.vivo.abe | That component requested the recorded package terminations, including during bounded execution | Its internal threshold, cloud whitelist, kernel-hook mechanism or a universal ban on all custom apps |
| Continuous Handler probe fired about 94ms late with screen off and device locked | In-process timing worked during that particular wake-lock-held interval | AlarmManager delivery was punctual, Doze never contributed to any previous delay, or overnight availability passed |
| Wake-lock cycle occurred, then com.vivo.abe force-stop preceded the ten-minute probe | Cycling did not prevent termination in that tested configuration | Every possible cycling policy is disproven, or a proven counter reset exists |
| Cycling trial used Battery Saver on, earlier short baseline used it off | The field trials are not a clean one-variable A/B comparison | A precise causal estimate of cycling's benefit or harm |
| Latest alarm-clock probe arrived 211.257s late while screen remained off; exit history shows REMOVE TASK before delivery | That probe missed its deadline; opening/unlocking was not required for its eventual delivery | The retained/locked-Recents configuration was validated, or force-stop removed this particular alarm (it later arrived) |
| Scheduled Termux commands have missing completion/Node reports | End-to-end execution has an unresolved failure domain after dispatch | Every missed claim was caused only by Android alarm delivery or only by the OEM killer |

A genuine force-stop is outside the recovery powers of an ordinary APK while it remains stopped. That is a platform boundary. It does not prove that no Android design can ever improve this device's operating behavior.

## 3. App-owned issues found in this review

### High: service promotion happens after blocking diagnostics

Scheduler.kt RunService.onStartCommand records SERVICE_STARTED and Schedule.record before startForeground. Diagnostics.emit synchronously collects multiple system-service values and writes SQLite; Schedule.record synchronously commits preferences. Android requires prompt foreground promotion. Move nonessential diagnostic work after foreground promotion and remove expensive instrumentation from that critical path. This is a confirmed ordering risk, not proof that it caused the com.vivo.abe force-stops.

Sources: app/src/main/java/ai/interlink/scheduler/Scheduler.kt (onStartCommand), Diagnostics.kt (emit/device).

### High: process-start timing is not measured at OS process start

DiagnosticApplication.onCreate registers a dynamic receiver before emitting PROCESS_STARTED, and logging performs synchronous initialization/IPC/storage. Receiver-entry logging also has synchronous work. The first app event cannot by itself partition total lateness into OS alarm dispatch, process startup, Application initialization and receiver handling.

Capture Process.getStartElapsedRealtime (public since API 24), Application entry/exit, receiver entry and foreground-promotion completion on the same elapsed-time base. Keep raw timestamps separate from later device-state sampling and storage completion. getStartRequestedElapsedRealtime (API 33+) can add the system start-request boundary where available. Reconcile wall/elapsed clock mappings explicitly rather than claiming that a callback timestamp is the kernel wakeup time.

Sources: Diagnostics.kt DiagnosticApplication.onCreate, AlarmReceiver.receive.

### High: two independently managed app lifecycles and unproven callback chain

The companion schedules and starts a foreground service, calls Termux RUN_COMMAND, and waits for Termux to start Bash/Node and return a PendingIntent report. Removing one bridge can reduce failure surface, but a native rewrite does not grant immunity from force-stop. Separate receiver delivery, service promotion, Termux dispatch, wrapper entry, Node startup, claims and callback evidence.

### Medium: continuous wake locks and cycling do not satisfy the battery objective

Continuous mode intentionally holds CPU wakefulness between sessions. Immediate release/reacquisition does not materially restore sleep time. The phone test did not establish that it prevents termination. It should remain an explicitly experimental, default-off option, not the recommended production architecture.

## 4. Options considered

| Option | Android executes claims? | Reduces known failure domains | Remaining limitation / cost | Decision |
|---|---|---|---|---|
| Harden current event-driven companion + Termux | Yes | Removes avoidable startup/diagnostic interference; preserves legitimate tested Node workflow | Two app lifecycles, OEM force-stop and real callback failures remain | Correct owned defects; best-effort until real acceptance evidence |
| Native Android API executor with bounded work | Yes | Removes Termux IPC, Bash/Node startup and result callback | Authentication/storage migration and business-logic parity effort; same OEM stop boundary | A legitimate simplification, not a demonstrated fix; do not start a rewrite solely on a promise of immunity |
| Termux-only cron + wake lock | Yes | Removes companion package and cross-app dispatch | Resident daemon/wake lock, battery cost, Termux still subject to OS/OEM management; unvalidated | Contingency experiment, not a guaranteed solution |
| Split each session into two independent short Android jobs | Yes | Reduces idle wait between opportunities | Five-minute separation can interact with idle-alarm quotas; ordinary WorkManager timing is unsuitable; exact alarm-clock use is user-visible and still OEM-dependent | Not a safe drop-in replacement without a separate timing/semantics design |
| Built-in Clock plus user action | Yes | Can use an existing alarm as a visible reminder | Requires user interaction; no supported arbitrary Termux callback assumed | Assisted fallback, does not satisfy unattended automation |
| Always-on host running one-shot Node sessions | No | Removes this phone's sleep, package-stop and Termux lifecycle from execution | New host availability, network/security/credential handling, phone-timezone synchronization; server/client compatibility must be validated | Stronger lifecycle-reliability alternative if deployment constraint is explicitly relaxed; not selected by default |
| Root/device-management or system-package policy changes | Depends | May change operating-policy assumptions | Device-wide impact, privileges, maintenance and reversibility; unavailable through ordinary APK permissions | Not part of normal app deployment; not an assumed solution |

No option guarantees twelve successful claims: independent server eligibility and verified acceptance are still required.

## 5. Decision and corrected recommendation

1. Reject the claim that the current Android build is reliable for unattended use on the affected device. Preserve its safe deadlines, authentication and durable duplicate barriers.
2. Do not label the on-device requirement universally impossible. The proven boundary is execution while the package is genuinely force-stopped; the field evidence does not establish every native/Termux/OEM configuration's behavior.
3. Keep Android as the requested target for this review. Fix the confirmed application critical-path risks before declaring that all observed delay is external. Do not promise that those fixes override a genuine OEM stop.
4. Do not begin a native rewrite, privileged-service addition, package-disable operation or off-phone migration merely because an experiment failed. Each changes distinct requirements and needs its own concrete rationale and validation.
5. Retain an always-on host as the stronger alternative when execution independence from this phone is prioritized. My earlier recommendation was reasonable as a conditional reliability fallback but too broad as a final replacement for the requested Android architecture. A development Mac is not automatically an always-on production host.
6. No further repetitive permission checks, speculative APK variations or new device tests are requested by this review. The user's available settings are accepted as enabled. This review documents the next owned engineering work and the remaining operating-system boundary.

## 6. Validation gates before a reliability claim

- Foreground promotion occurs before expensive diagnostic/storage work; inspect that ordering and test under delayed diagnostic sampling without delaying promotion.
- Measure OS process start, Application/receiver entry, foreground promotion, wrapper/Node entry and API stages independently. Show the distribution of where time is lost; do not infer a kernel timestamp from an app log label.
- Run the actual supported Termux bootstrap/callback chain with safe no-claim checks, including missing dependency/report/timeout behavior. Mocked API tests and a local Handler probe are not substitutes for cross-app execution.
- Retain the existing physical acceptance campaign: at least 48 hours/12 due sessions with all stages accounted for, locked-screen and overnight coverage, bounded battery impact, no duplicate submission, no requests outside original windows, and two independent confirmations when the server reports eligibility twice.
- A failure with a named OEM force-stop is reported as such; a missing callback remains unknown, not a fabricated claim failure or success. No expired catch-up claims.

## 7. Primary references

- Foreground promotion timing: https://developer.android.com/develop/background-work/services/fgs/troubleshooting
- Startup/main-thread initialization: https://developer.android.com/topic/performance/issues/launch-time
- OS process-start timestamps: https://developer.android.com/reference/android/os/Process
- Alarm timing/quotas: https://developer.android.com/reference/android/app/AlarmManager
- Termux supported integration: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Force-stop semantics in Android framework: https://android.googlesource.com/platform/frameworks/base/+/f4918271134e72da0e55436776480740b283997a/core/java/android/app/ActivityManager.java
