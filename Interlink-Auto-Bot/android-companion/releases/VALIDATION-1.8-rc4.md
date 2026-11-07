# Wake-lock cycling experiment validation — 1.8-rc4

2026-11-07. Version code 15. Optional/default-off experiment; no established OEM-counter reset or force-stop immunity.

Executed:

- Build, assemble and lint passed: 0 errors, 46 warnings.
- 13 Android JVM tests passed. New policy tests cover the actual 120-second interval and preserving the earlier original wall-clock/elapsed deadline after clock changes or suspension.
- API 35 emulator test ran for 125.592 seconds using the real interval. It observed a wake-lock cycle after at least 120 seconds and the subsequent no-claim probe firing while the screen was still off. It asserted that no claim execution was dispatched and stopped the service afterward.
- Routine emulator instrumentation: 19 passed, 6 opt-in skipped (runner reports OK (25 tests)). Experiment remains off by default.
- APK signature verified; final APK and test APK installed successfully on the emulator. Git whitespace check passed.

Not executed:

- vivo/iQOO policy behavior with cycling enabled, physical 10-minute/overnight survival or battery comparison. The emulator does not contain com.vivo.abe.
- Actual live RunService cycle during API requests. Its deadline policy is unit-tested; no live claims or tokens were used. Termux's own wake locks are unchanged.

The device settings are accepted as enabled per the user. The iManager UI need not be opened for the system component named in historical force-stop descriptions to act. No unrelated accessibility/listener access or system-package disabling is introduced.

Evidence: /tmp/interlink-cycle-build.log, /tmp/interlink-cycle-real-interval.log, /tmp/interlink-cycle-routine.log.

SHA-256: d449cea5f661a6c3913060bc7d90b9d719deec1c5d6d5a74ec5b87f9a999d502.
