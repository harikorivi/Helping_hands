# Interlink Scheduler 1.6

Install [Interlink-Scheduler-1.6.apk](Interlink-Scheduler-1.6.apk) with the current Node.js source. Follow the [installation and upgrade guide](../../README-mobile.md). No re-login is required if existing credentials still work.

Personal debug-signed APK, package `ai.interlink.scheduler`, version code 7, Android 8 minimum. Credentials remain in Termux and are not bundled.

Changes:

- Recover remaining valid claim windows when opening or repairing an enabled schedule, retaining the original deadline and duplicate protection.
- Two-minute, no-claim background alarm test with independent cancellation and stage-specific diagnostics.
- Device and power-state diagnostics; exact-alarm capability wording corrected to include battery-exemption access.
- Retains local today/yesterday log cleanup and six independent daily alarms.

The original iQOO/Funtouch missing-delivery cause remains unverified. This release is a recovery fix and diagnostic build, not a guarantee of background execution after dismissal. See [VALIDATION-1.6.md](VALIDATION-1.6.md) for tests and limits.

Built with JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3 and Kotlin 2.0.21. Build and lint passed (0 errors, 32 warnings); signature verification passed.

SHA-256:

```text
e50daab7c7fe3ff6f0dc98a6b175d861305923cd42ba19090a2a504db5484ba9
```

Use the same private signing key for compatible updates; never commit keys. Older APKs are historical releases.
