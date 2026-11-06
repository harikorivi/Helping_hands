# Candidate verification — 1.7-rc1

Date: 2026-11-06. Package `ai.interlink.scheduler`, version code 8. **Production acceptance NOT MET; physical root cause OPEN.**

## Executed locally

- Node: 34/34 tests passed, using fake responses/isolated temporary state; no live claims or credentials. Includes independent windows, uncertain submissions, token refresh, report redaction/correlation, expired Node entrypoint and diagnostic statistics.
- Android JVM: 9/9 passed.
- Final APK Android 15 API 35 emulator instrumentation: 13 passed; 3 opt-in tests skipped in the normal invocation (runner summary includes these in “OK (16 tests)”).
- Separately enabled forced-Doze receiver test: 1/1 passed. This proves only that bounded emulator scenario, not the phone’s overnight delivery.
- Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: successful. Lint: 0 errors, 34 warnings.
- Final app and test APK installed successfully on emulator-5580. APK signature verified with SDK apksigner.jar using JDK 17 (v2 signature, one signer).
- Shell syntax and Git whitespace checks passed.

Build tools: JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21.

Local development logs: `/tmp/interlink-rc-node.log`, `/tmp/interlink-rc-build.log`, `/tmp/interlink-rc-instrument-final.log`, `/tmp/interlink-rc-doze.log`. They are not bundled in the APK.

## Not executed / release blockers

No physical phone was connected. No complete supported Termux execution/callback chain was tested on a real device. No candidate four-hour, overnight, OEM restriction or real two-claim campaign was executed. Mocked claims and emulator alarm tests do not prove those scenarios. Earlier user logs are historical evidence, not candidate acceptance.

The [T1–T18 matrix and acceptance campaign](../../docs/RELIABILITY-VALIDATION.md) records partial/not-run scenarios and requires at least 48 hours with all 12 due scheduled slots accounted for, independent eligibility evidence, and no unexplained missing stages. No force-stop recovery is promised. No root cause is assigned to Funtouch, permissions or battery saver without evidence.

APK SHA-256: `45e93a74c613ec99895468d4e1ba974a2b204cb661024c740984c334066a6667`.
