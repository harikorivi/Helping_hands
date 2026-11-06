# Version 1.4 validation — 6 November 2026

## Completed checks

- 23 Node.js tests passed, including the 01:30:33 suspension regression, resume beyond deadline without network access, true clock changes, bounded waits, token refresh, eligibility validation and submission replay protection.
- 6 Android JVM tests passed: independent slots, local-date/DST planning, delayed delivery boundaries, elapsed-realtime deadlines and mode-specific outcomes.
- 6 normal Android 15 emulator integration checks passed: six-slot registration and cancellation; late/duplicate delivery preserving other slots; old callback isolation; distinguishing overdue recovery from delivery; boot/timezone receiver recovery; actual OS PendingIntent delivery with an expired payload.
- The seventh integration method is opt-in. It passed separately with `testDoze=true`: emulator battery unplugged, screen asleep and forced idle; an allow-while-idle alarm was delivered and its expired payload was rejected without dispatching Termux. Power settings were restored afterward.
- APK assembly and lint passed: 0 errors, 20 warnings. APK signature verification passed.
- Shell syntax and Git whitespace checks passed.

No test called a live Interlink account or submitted a claim. Emulator installations were temporary; no physical phone was changed. The normal integration suite skips the Doze method unless explicitly requested, because repeated idle alarms can encounter Android per-app quotas.

## Reproduction

From `Interlink-Auto-Bot`:

```sh
npm ci --ignore-scripts
npm test
bash -n run-scheduled.sh run-on-demand.sh setup-termux.sh
```

From `android-companion`, with JDK 17, SDK 35 and Gradle 8.11.1:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

On a disposable emulator only, install both the app and test APK, grant the exact-alarm app-op, then invoke the tests. Replace `emulator-5580` with that emulator's serial; do not run these fixture tests on a configured personal phone because they reset the companion's test preferences.

```sh
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell appops set ai.interlink.scheduler SCHEDULE_EXACT_ALARM allow
adb -s emulator-5580 shell am instrument -w ai.interlink.scheduler.test/androidx.test.runner.AndroidJUnitRunner
```

Run the opt-in idle test separately, allowing for OS alarm quotas:

```sh
adb -s emulator-5580 shell am instrument -w -e testDoze true -e class 'ai.interlink.scheduler.SchedulingIntegrationTest#idleAlarmDeliveryWhenRequested' ai.interlink.scheduler.test/androidx.test.runner.AndroidJUnitRunner
```

## Limits

A simulated long suspension and an emulator Doze delivery test are not an overnight test on every OEM phone. Verify a real locked-screen scheduled run after upgrading both components. Android can still delay or suppress work due to force-stop, revoked permissions, OEM restrictions, shutdown, missing network, or unavailable Termux. Cleanup cannot run while the OS suspends a process; when CPU time returns, the app rechecks deadlines and refuses further late requests. Already-submitted network requests can have ambiguous outcomes.

Use **Copy diagnostic history** plus the private claim log to investigate failures. Do not share credentials, raw account responses, or signing keys.
