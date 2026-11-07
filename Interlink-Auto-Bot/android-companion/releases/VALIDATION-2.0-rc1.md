# Service-only validation — 2.0-rc1

2026-11-07. Package `ai.interlink.scheduler`, version code 16. Personal debug-signed candidate; physical vivo acceptance pending.

## Executed

- Gradle offline build: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` passed. Lint: 0 errors, 21 warnings (primarily deliberate durable preference commits and untranslated personal-app UI text).
- 14 Android JVM tests passed: local calendar/DST planning, independent slots, original deadlines, elapsed-clock limits, retention and safe exit descriptions.
- 38 Node tests passed, including authentication refresh/storage, two independent eligible windows, safe retries, result verification, diagnostics and log retention. Requests are mocked; no real login or claim occurred.
- API 35 emulator routine instrumentation: runner `OK (21 tests)`: 20 passed and one opt-in UI test skipped. A separate opt-in UI run passed that remaining test in approximately 14 seconds.
- Actual Recents swipe produced `SERVICE_TASK_REMOVED`; the foreground service and wake lock remained active, and the no-claim service timer fired while the screen remained off. This emulator has no secure device credential; this proves screen-off behavior, not secure-lock/OEM equivalence.
- Service start with the diagnostic executor deliberately blocked still completed foreground promotion and acquired its wake lock.
- Single `specialUse` service declaration, `stopWithTask=false`, six planned slots and absence of exact-alarm permission verified from installed package metadata.
- Due original timestamp forwarded to the supported bootstrap. Duplicate accepted slot, duplicate manual request, consumed one-shot callback and stale callback did not create another dispatch or stop the continuous scheduler.
- Actual Android PendingIntent delivery completed the matching manual fixture and preserved the running service. Termux dispatches were intercepted, not sent to a real Termux installation.
- Pause allowed a pending fixture to finish before stopping the service. Expired prior leases became unknown without resubmission. Reboot recovery and a changed boot identity invalidated old monotonic deadlines; timezone replanning preserved the active original deadline.
- Legacy alarm identities canceled without canceling an unrelated PendingIntent. Legacy slots and accepted delivery markers migrated without replaying an already-delivered session; unrelated preferences survived.
- Missing Node report could not become two successes. Correlation mismatch rejected. Independent success/not-eligible window reasons preserved. Termux internal error could not become a passed manual test.
- Private diagnostics exported to Downloads and read back as UTF-8 without broad storage permission. Live-debug off/on gating preserved core events.
- APK signature verified; certificate SHA-256 matches 1.8-rc4: `5210e3b887ca7b7128b3057b456d1ea35ee5d46f1776d041665eb64899e1ebab`. Existing-app update installed successfully. Git whitespace check passed.

## Not established

No physical vivo/iQOO service-survival, full overnight/six-run, battery-discharge or real two-claim acceptance was executed on the Mac. The emulator does not include vivo's power framework. Simulated boot broadcasts are not a physical reboot or first-unlock acceptance test. The real Termux/Node/API handoff still requires phone evidence; intercepted IPC plus synthetic callbacks do not prove it.

One visible foreground service and a CPU wake lock reduce application-owned lifecycle conflicts; they cannot override a genuine OEM/Android force-stop. A timer probe proves the timer only. The production gate remains the 48-hour physical campaign in the [service ADR](../../docs/ADR-SERVICE-ONLY.md).

Local evidence: `/tmp/interlink-service2-build.log`, `/tmp/interlink-service2-node.log`, `/tmp/interlink-service2-instrumentation.log`, `/tmp/interlink-service2-recents.log` and generated Gradle test/lint reports. These temporary files are not embedded in the APK or committed as user data.

APK SHA-256: `c6aaa9d9c2e405d429cf747c3c2c7ec220c717554eca6846f0da153c69ca219f`.
