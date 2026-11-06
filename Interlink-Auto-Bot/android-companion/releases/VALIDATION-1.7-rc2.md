# Diagnostic export validation — 1.7-rc2

2026-11-06. APK version code 9. Scheduling and Node behavior unchanged from 1.7-rc1.

- Final APK build and lint passed: 0 errors, 34 warnings.
- Focused Android 15/API 35 emulator test passed: export creates a readable UTF-8 file in `Download/Interlink Scheduler/`, preserves expected filename/content, and clears MediaStore pending state. The test deletes its fixture afterward.
- Initial test found Android appended `.txt` to `.log`; the export now uses a `.txt` name. The corrected final APK passed the test.
- APK signature verified using SDK apksigner with JDK 17; installation over the existing emulator app succeeded.
- Android 8/9 save-picker flow inspected and compiled, not device-tested. Actual phone File Manager behavior not tested. No live API requests or claims performed.
- Prior 1.7-rc1 broader test results remain historical; this update reran the focused export test and build/lint only.
- No claim of fixing the reported unlock-dependent delay. Physical overnight and multi-hour acceptance remain open under the existing validation plan.

Local logs: `/tmp/interlink-export-final-build.log` and `/tmp/interlink-export-final-test.log`.

SHA-256: `cd81371f7765c7aae61bb897efb23d6fb3734d064090e27fdfdf6b6eaec6eda8`.
