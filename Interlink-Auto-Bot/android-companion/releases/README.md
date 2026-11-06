# Interlink Scheduler 1.5

Install [Interlink-Scheduler-1.5.apk](Interlink-Scheduler-1.5.apk) together with the current Node.js source. Follow the [installation and upgrade guide](../../README-mobile.md). Both components must be updated for log cleanup; no re-login is required if existing credentials still work.

Personal debug-signed APK, package `ai.interlink.scheduler`, version code 6, Android 8 minimum. Credentials remain in Termux and are not bundled.

Changes:

- Automatic retention of today and yesterday’s Termux daily logs and companion activity, using local calendar dates.
- Cleanup on execution/app activity without a background cleanup timer; credentials and claim records remain untouched.

- Six independent daily alarm slots, with recovery after reboot/update/time changes.
- Suspend-aware Node clock, original-window checks, and resumed deadline handling.
- Separate connection-test, manual-claim and scheduled-run messages.
- Intended and actual alarm times, late-callback isolation and copyable diagnostic history.
- Explicit battery-exemption prerequisites and wrapper-entry/busy logging.

Built with JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3 and Kotlin 2.0.21. Build and lint passed (0 errors, 21 warnings); signature verification passed. Tests and limitations are documented in [VALIDATION-1.5.md](VALIDATION-1.5.md).

SHA-256:

```text
b0c9e890e216f1046e684527ebc1b6496f857960409443f2f1dae4e72fd69256
```

Use the same private signing key for compatible updates; never commit keys. Older APKs retained in this directory are historical versions, not the recommended installation.
