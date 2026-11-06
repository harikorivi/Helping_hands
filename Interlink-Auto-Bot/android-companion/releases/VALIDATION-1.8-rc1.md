# Continuous scheduling validation — 1.8-rc1

2026-11-06. Android companion version code 12. Node logic and original six daily windows unchanged.

- Build/assemble and lint passed: 0 errors, 43 warnings.
- 9 Android JVM tests passed. Normal API 35 emulator instrumentation: 18 passed, 4 opt-in skipped (runner reports `OK (22 tests)`). This tested starting the foreground monitor, acquiring its wake lock, keeping six alarms registered, and stopping/releasing the service.
- After adding the no-claim two-minute timer, the focused final emulator test passed: the timer fired, reported its event, and did not start a claim. The final app/test APKs compiled and lint passed.
- APK update installation and v2 signature verification passed.
- The continuous timer uses the original stored daily alarm timestamp. The receiver's shared delivered marker and Node ledger guard duplicates; original claim deadlines remain enforced. A real six-slot phone run has not yet exercised this mode.
- Physical vivo locked-screen, swipe, overnight, battery-drain, reboot and OEM termination scenarios are NOT RUN. No live claim was submitted from the Mac. A foreground service and partial wake lock do not guarantee survival after force-stop or manufacturer termination.
- The `specialUse` foreground service uses a visible notification and user opt-in; Google Play distribution may require additional review. This personal debug APK is sideloaded.

Development evidence: `/tmp/interlink-continuous-final-build.log`, `/tmp/interlink-continuous-tests.log`, `/tmp/interlink-continuous-test-build.log`, `/tmp/interlink-continuous-probe-test.log`.

SHA-256: `ddba3ff488d18b3fcade238bcc027283c3ea7ca31d06ea0326201c3c74f37c4f`.
