# Live diagnostics validation — 1.7-rc3

2026-11-06. APK version code 10. Scheduling and Node claim behavior unchanged.

- Build and lint passed: 0 errors, 36 warnings.
- Two focused Android 15/API 35 emulator tests passed: MediaStore Downloads filename/content/publication; debug-off suppresses optional events while core evidence remains, debug-on records events, snapshot includes lock/interactive/debug state, and exported report includes them.
- APK signature verified and update installation succeeded on the emulator.
- Physical screen/unlock event delivery and activity visibility on the affected Funtouch device remain untested. The tests do not establish event delivery during process death, overnight reliability or the root cause of delayed alarms.
- No live claims executed. No persistent service, extra wake lock or polling introduced. Existing broader test results remain historical; this change ran focused tests plus build/lint.

Local logs: `/tmp/interlink-debug-build.log`, `/tmp/interlink-debug-test.log`.

SHA-256: `ba8ad13bc4702f00a98bb3109f08030f3f87a96975f226bd5f8ec59e1cd8159d`.
