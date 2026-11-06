# Interlink Scheduler 1.4

Install [Interlink-Scheduler-1.4.apk](Interlink-Scheduler-1.4.apk) together with the current Node.js source. Follow the [installation and upgrade guide](../../README-mobile.md). Both components must be updated from 1.3; no re-login is required if existing credentials still work.

Personal debug-signed APK, package `ai.interlink.scheduler`, version code 5, Android 8 minimum. Credentials remain in Termux and are not bundled.

Changes:

- Six independent daily alarm slots, with recovery after reboot/update/time changes.
- Suspend-aware Node clock, original-window checks, and resumed deadline handling.
- Separate connection-test, manual-claim and scheduled-run messages.
- Intended and actual alarm times, late-callback isolation and copyable diagnostic history.
- Explicit battery-exemption prerequisites and wrapper-entry/busy logging.

Built with JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3 and Kotlin 2.0.21. Build and lint passed (0 errors, 20 warnings); signature verification passed. Tests and limitations are documented in [VALIDATION-1.4.md](VALIDATION-1.4.md).

SHA-256:

```text
55e4fcddd6e0a77e3a8f4a753e2749caef736ed04c6f4018af843ae732db44c6
```

Use the same private signing key for compatible updates; never commit keys. Older APKs retained in this directory are historical versions, not the recommended installation.
