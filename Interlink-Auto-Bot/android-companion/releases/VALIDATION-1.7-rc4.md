# Alarm-clock comparison validation — 1.7-rc4

2026-11-06. Version code 11. Node and six-slot scheduling behavior unchanged.

- Build, 9 JVM tests and lint passed. Lint: 0 errors, 36 warnings.
- Normal API 35 emulator instrumentation: 17 tests passed; 4 opt-in tests skipped (runner prints “OK (21 tests)”). Includes alarm-clock registration/cancellation preserving daily slots, receiver delivery without opening UI, debug controls and file export.
- Separately enabled alarm-clock forced-idle test: 1 passed. Test put the emulator into forced idle, registered a short alarm-clock probe, observed receiver delivery, and restored power/screen settings. Execution was marked busy to prevent Termux launch or any API/claim operation.
- APK update installation and signature verification passed.
- Not performed: physical Funtouch ten-minute or multi-hour test, real Termux execution using this mechanism, or two-claim acceptance. No production-readiness or permanent-fix claim.

Logs: `/tmp/interlink-clock-build.log`, `/tmp/interlink-clock-test-build.log`, `/tmp/interlink-clock-tests.log`, `/tmp/interlink-clock-idle.log`.

SHA-256: `d1055a06b6188816ae12ea1aab2c48a47f68692b4a36975b0054f0534caa2c80`.
