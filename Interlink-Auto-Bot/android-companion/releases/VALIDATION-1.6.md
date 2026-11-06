# Version 1.6 validation — 6 November 2026

## Changes and unresolved issue

Foreground recovery now dispatches an undelivered, still-valid scheduled slot with its original timestamp and deadline. Expired windows are not replayed. The app has a cancellable two-minute background connection-test alarm, separate from daily alarms, with no claim path. Diagnostics distinguish registration, delivery, service entry and command result. Exact-alarm capability is no longer described as proof of a granted Settings toggle: Android may provide it through battery exemption.

The missing delivery on the user's iQOO 7 / Funtouch OS 14 / Android 14 remains **unresolved on that device**. This release fixes the observed recovery defect and supplies a reproducible diagnostic, not a guarantee of OEM background execution.

## Completed validation

- 27 Node.js tests passed. Added recovery-after-first-window coverage: skip the expired first window, execute the second and never resubmit on replay.
- 9 Android JVM tests passed.
- Final APK emulator suite: 9 integration tests passed; 3 opt-in methods skipped. Includes recovery at original start +3m35s, preserved timestamp, no duplicate recovery, expired recovery rejection, independent probe cancellation/late-delivery behavior and existing scheduling/callback regressions.
- Separate lifecycle probe: scheduled a diagnostic alarm for 20 seconds ahead, stopped instrumentation, locked the emulator screen and killed the background app process using `am kill` (not force-stop). Verified alarm delivery and entry into the connection-test service from a subsequent instrumentation invocation.
- Separate Recent Apps test: scheduled the same probe, opened the app, swiped its card away, verified the card was absent in the launcher UI hierarchy, then locked the screen. Verified alarm delivery and connection-test service entry.
- Separate forced-Doze test passed; it uses an expired payload to verify delivery while preventing any claim execution. Battery/screen state restored after the test.
- Lifecycle and Doze tests ran on the 1.6 candidate before the final callback-status isolation adjustment. The complete normal regression suite was rerun on the final APK.
- Android 15/API 35 emulator only. No physical device was modified, no live Interlink credentials were used, and no claims were submitted. Termux/API success was not tested end to end; the lifecycle assertions prove receiver and service startup, not network success.
- Build, shell syntax and Git whitespace checks passed. Lint: 0 errors, 32 warnings (including synchronous preference persistence, existing deprecated APIs/path/dependency/backup guidance). APK v2 signature verified; version code 7, version name 1.6.

## Reproduction

Build with the installed JDK 17, SDK 35, Gradle 8.11.1 toolchain:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Install app and test APKs on a disposable emulator only; instrumentation clears scheduler preferences. Grant the exact-alarm app-op, then run the normal suite as described in [VALIDATION-1.4.md](VALIDATION-1.4.md).

For lifecycle testing, invoke `LifecycleProbeTest#arm` with `-e lifecycleProbe true`, terminate instrumentation, dismiss or kill the app without force-stop, wait beyond the 20-second test alarm, then invoke `LifecycleProbeTest#verify` with the same argument. These opt-in methods never invoke claim mode.

On the affected phone, use the public **Test background alarm • in 2 minutes** button. First test Home + screen lock; then repeat separately after Recent Apps dismissal. Avoid minutes 24–34 of scheduled hours. Copy diagnostic history after each test. Registration alone is not success. Repeated idle tests can encounter Android alarm quotas. A force-stop remains unsupported.
