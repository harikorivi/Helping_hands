# 2.0-rc3 — 48-hour iQOO 7 acceptance checklist

This is the release/physical verification standard, not a statement that the campaign passed.

## Preparation

- Record APK version/code, vivo I2012 / Android 14 build, phone timezone, test start/end and intended six daily times.
- Verify native Scheduler battery exemption and intended Termux power access, `canScheduleExactAlarms()`, Termux command permission and notifications. Keep Battery Saver off. Record charging state and the chosen Recents scenario.
- Leave **48-hour trial evidence** enabled. It retains three local dates/6,000 structured rows, while existing Termux daily logs still retain today/yesterday. Keep ordinary manual claims/tests outside the unattended campaign.
- Confirm six accepted registrations and the next intended time. Idle execution service and `wake lock=false` between sessions are expected.

## Per-session evidence

- `ALARM_SCHEDULED`: original `executionId` / `scheduledAt`, hour, method and generation. Registration alone is not delivery.
- `ALARM_RECEIVED`: original execution ID, fresh attempt ID, receiver wall time, receiver elapsed time, PID and delay.
- `FOREGROUND_PROMOTED`: same attempt, service ID, service-entry and promotion wall/elapsed timestamps. Measure receiver→service and service-entry→promotion separately from alarm lateness.
- `TERMUX_DISPATCHED` then `NODE_PROCESS_STARTED`: match original execution/attempt/service; retain Node PID and Node start time. A sent command is not proof of Node start.
- Both `CLAIM_WINDOW_1_COMPLETED` and `CLAIM_WINDOW_2_COMPLETED`: independent result/reason, submission state and safe request/HTTP metadata. Report missing/uncertain evidence as unknown.
- `NODE_PROCESS_EXITED`, `EXECUTION_COMPLETED/FAILED`, then `EXECUTION_RESOURCES_RELEASED`: service, notification and wake lock end without extending the original deadline. Probe sessions explicitly expect no Node or claims.

Exported `actualAt` is event time; `recordedAt`/`deviceSampledAt` are later storage/sampling times. `pid` is the event-source PID and `observerPid` is the importer PID. Power state includes battery exemptions, exact-alarm capability, standby bucket, Battery Saver, idle, screen/lock and charging state. Never interpret callback-time Node-import sampling as historical Node power state.

## Process exits

For every retained `PROCESS_EXIT_OBSERVED`, inspect `details.exitAt`, exited `details.pid`, reason code/name, status, importance and optional redacted description. The observer PID/time describe reading history, not the original termination. A named force-stop requester is evidence of that request, not a proven whitelist/watchdog mechanism. Record gaps and start rejections explicitly.

## Pass/fail

- Prefer 48 uninterrupted hours with all 12 due sessions accounted for, including overnight and screen-locked periods.
- Initial engineering target: receiver latency ≤30 seconds. This is a measured target, not an Android/OEM guarantee. Report every larger delay even if a remaining window still succeeds.
- No app-open intervention needed for delivery; no duplicate or out-of-window submission; no resident execution service/lock between jobs.
- Both window outcomes are present. Server not-eligible is a legitimate business outcome; confirmed claim success requires the existing API-acceptance/advancing-timestamp verification. No promise of 12 daily successes.
- Do not silently combine trials with different settings. Label interruptions, calls, unlocks, reboots, charging, task removal/locking and any manual repair.
- Test first-unlock reboot, APK-update recovery, pause/re-enable, permission denial/regrant, network failure and clock/timezone changes separately from the unattended baseline. Keep clock-change/duplicate boundary testing isolated from live claims.
- Save **Downloads → Interlink Scheduler → Interlink-diagnostics-*.txt** after the campaign. Share the file, never credentials or raw API bodies. A genuine OEM force-stop remains a failed unattended trial, not a claimed app-level bypass.
