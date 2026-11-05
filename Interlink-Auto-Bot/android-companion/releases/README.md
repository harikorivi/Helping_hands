# Interlink Scheduler 1.3

Install [Interlink-Scheduler-1.3.apk](Interlink-Scheduler-1.3.apk) with the Node.js source from this same repository version. Follow the [first-time installation guide](../../README-mobile.md).

Personal debug-signed build, package `ai.interlink.scheduler`, version code 4, Android 8 minimum. It contains the guided setup, teal launcher icon, connection test and confirmed single-claim controls. Credentials stay in Termux and are not bundled in the APK.

Built with JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3 and Kotlin 2.0.21. Build and lint passed (0 errors, 15 warnings); APK signature verification passed. The Node.js suite passed 19 tests. Emulator UI inspection does not establish physical-phone background reliability.

SHA-256:

```text
c87a448b5d0aa745c7e671e27f48176ad9ff594fea6cd2b066e991b677afcb62
```

Rebuilds intended to update this APK must use the same private signing key. Do not commit signing keys or credentials. A production distribution should use an appropriately managed release signing key.
