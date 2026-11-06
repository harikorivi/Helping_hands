# Alarm Delivery Check 1.0 validation

Built 6 November 2026 with SDK 35, JDK 17, Gradle 8.11.1 and AGP 8.7.3. Separate application ID: `ai.interlink.diagnostics`; minimum Android 8/API 26.

- 3 JVM tests passed: pending/overdue distinction, delayed delivery correlation and cancellation/early-delay representation.
- 2 API 35 emulator instrumented tests passed: actual OS PendingIntent delivery and duplicate suppression; cancellation and read-only report generation without replay.
- App activity launched on the emulator.
- Build passed; lint: 0 errors, 8 warnings. APK v2 signature verification passed.
- No network permission, live API calls, credentials, Termux commands or claims. No phone installation was performed.
- Physical iQOO/Funtouch behavior, overnight reliability and this new package's dismissal/Doze behavior remain unverified. These are the user-facing comparative tests, not claims of guaranteed operation. Prior scheduler emulator tests do not substitute for this package's physical-device tests.

SHA-256 of `Alarm-Delivery-Check-1.0.apk`:

```text
8b50628ecbf5b05895dc7eee835402b3e9755b5881695ca1522f881ab7f6fb7e
```

See [installation and interpretation](../alarm-diagnostics/README.md). Installation is independent of the scheduler and does not require updating Termux.
