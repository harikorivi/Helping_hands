# Interlink Scheduler 1.7-rc1 — validation candidate

Install [Interlink-Scheduler-1.7-rc1.apk](Interlink-Scheduler-1.7-rc1.apk) **with the matching current Node.js source**. Follow the [installation and upgrade guide](../../README-mobile.md). Working stored credentials do not require re-login.

**Not production-validated. The physical phone’s missing-alarm root cause remains open.** This candidate fixes confirmed diagnostic and lifecycle gaps; it does not guarantee uninterrupted Android execution. Four-hour and overnight physical tests remain release blockers.

Personal debug-signed APK: package `ai.interlink.scheduler`, version code 8, minimum Android 8. Credentials are not bundled. Use the same private signing key for compatible updates; never commit keys. Older APKs are historical releases.

Includes correlated persistent lifecycle events, independent claim-window outcomes, safe Node-to-companion reports, explicit unknown/error states, delivery statistics, structured export, and 4h/8h no-claim probes. See the [audit](../../docs/RELIABILITY-AUDIT.md), [changelog](../../docs/RELIABILITY-CHANGELOG.md), and [T1–T18 matrix](../../docs/RELIABILITY-VALIDATION.md).

[Candidate verification](VALIDATION-1.7-rc1.md): 34 Node tests, 9 JVM tests, 13 normal emulator tests plus one separate forced-Doze test passed. Build/lint passed (0 errors, 34 warnings). APK v2 signature verified. This is not full Termux/physical-phone testing.

SHA-256:

```text
45e93a74c613ec99895468d4e1ba974a2b204cb661024c740984c334066a6667
```

## Separate offline diagnostic app

[Alarm-Delivery-Check-1.0.apk](Alarm-Delivery-Check-1.0.apk) installs alongside the scheduler and needs no USB/network pairing, Termux or account. It tests its own alarm delivery and copies a report for manual sharing. It cannot read privileged system logs or another app's private state. See [instructions](../alarm-diagnostics/README.md) and [validation](VALIDATION-ALARM-CHECK-1.0.md).
