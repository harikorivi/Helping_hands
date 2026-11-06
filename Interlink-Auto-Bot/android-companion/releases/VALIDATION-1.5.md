# Version 1.5 validation

Validated on 6 November 2026 on the development Mac:

- Node.js: 26 tests passed, including safe log selection, year boundaries, missing paths and symlink preservation.
- Android JVM: 9 tests passed, including local midnight, DST, year boundaries, legacy entries and the 100-entry cap.
- Shell syntax: both execution wrappers passed `bash -n`.
- Debug APK assembled; lint reported 0 errors and 21 warnings. The additional warning concerns synchronous persistence of pruned activity; synchronous writes are retained for durable history.
- APK signature verification passed (v2), version 1.5, version code 6.

Cleanup keeps today and yesterday by local calendar date. Termux cleanup runs under the shared execution lock; companion cleanup runs on app opening and event recording. It does not delete credentials, claim ledgers or scheduling state. Future-dated logs and unknown formats are preserved conservatively (companion entries remain subject to its count cap).

This release was not installed or tested on the user’s physical phone. No live claims were made for validation. Earlier scheduling/emulator validation is recorded in [VALIDATION-1.4.md](VALIDATION-1.4.md); those device tests were not repeated for this retention-only change. Android execution restrictions still apply.
