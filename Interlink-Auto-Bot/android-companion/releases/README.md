# Interlink Scheduler 1.7-rc3 — configurable live diagnostics

[Download Interlink-Scheduler-1.7-rc3.apk](Interlink-Scheduler-1.7-rc3.apk). Install over the existing app; do not uninstall. Package `ai.interlink.scheduler`, version code 10. Existing 1.7-rc1/rc2 Node source does not need another update for these Android-only features. No re-login required.

Enable **Live testing diagnostics → Live debug logging**, then optionally tap **Record system state now**. After testing, use **Save diagnostic log to Downloads** and share the text file from **Downloads → Interlink Scheduler**. Turn extra debugging off when finished; core execution evidence stays on.

Records app visibility, observed screen/unlock/power transitions and system state while the process exists. It cannot observe events while killed and cannot distinguish Home from swipe solely from activity visibility. **This does not fix or establish the cause of delayed alarm delivery on unlock.**

[Instructions](../../README-mobile.md) · [Validation](VALIDATION-1.7-rc3.md).

SHA-256: `ba8ad13bc4702f00a98bb3109f08030f3f87a96975f226bd5f8ec59e1cd8159d`.

## Previous export candidate 1.7-rc2

[Download Interlink-Scheduler-1.7-rc2.apk](Interlink-Scheduler-1.7-rc2.apk). Install over the existing app; do not uninstall. Version code 9, package `ai.interlink.scheduler`, same signing identity. If already using the 1.7-rc1 Node source, this feature only needs the APK update; no login or Node change is needed.

Use **Execution evidence → Save diagnostic log to Downloads**. Android 10+ saves a text log under **Downloads → Interlink Scheduler**; Android 8/9 uses the save picker. Share the file from File Manager. No storage permission, USB pairing or large clipboard paste is needed. Saved exports remain until manually deleted.

This is an export update, **not a fix for delayed alarms on unlock**. Physical-device reliability remains unvalidated. [Installation guide](../../README-mobile.md) · [Validation](VALIDATION-1.7-rc2.md).

SHA-256: `cd81371f7765c7aae61bb897efb23d6fb3734d064090e27fdfdf6b6eaec6eda8`.

## Previous candidate and architecture evidence

### Interlink Scheduler 1.7-rc1 — validation candidate

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
