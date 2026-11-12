# Alarm-clock / bounded-service validation — 2.0-rc3

12 November 2026. Package `ai.interlink.scheduler`, version code 18, min API 26 / target API 35. Personal debug-signed validation candidate. The physical 48-hour iQOO 7 campaign is pending.

## Executed local checks

- Offline JDK 17 / SDK 35 / Gradle 8.11.1 build, unit tests, main/test APK assembly and lint passed. Lint: 0 errors, 31 warnings; durable preference commits, fixed Termux cross-app path, bounded-lock flow analysis, UI text and direct battery-exemption policy warnings remain visible. No public-store policy approval is claimed.
- 14 Android JVM tests passed: six local-calendar slots, DST/calendar planning, wall/elapsed deadlines, retention and safe exit descriptions.
- 38 mocked Node tests passed, including authentication refresh/persistence, independent two-window outcomes, safe request/claim verification, duplicate barriers and log cleanup. Node business logic and token files were not changed.
- API 35 routine instrumentation: `OK (26 tests)` — 24 passed; the denied-permission scenario and opt-in launcher test were skipped in this routine invocation.
- Real launcher Recents removal plus screen-off alarm-clock probe: `OK (1 test)`, 16.413 seconds. Verified no service or execution wake lock before the probe, a new OS receiver event after UI task removal, correlated foreground promotion, no Termux dispatch, and return to idle with service/handoff locks released while the screen stayed off.
- Forced-idle comparison: the same real launcher/screen-off test passed with `deviceidle force-idle`; 16.575 seconds. Idle and simulated battery state were restored afterward. These tests exercise an AOSP emulator, not vivo firmware or a secure-PIN first-unlock condition.
- Native exact-alarm app-op was denied outside instrumentation, then a dedicated suite passed (`OK (2 tests)`, 2.690 seconds). Missing capability prevented registration/service launch; the UI requested the native permission page without treating a canceled dialog as approval. The OEM App Info fallback was exercised again. Exact-alarm app-op was restored afterward. A separate regression verified that regrant clears the stale missing-capability warning without enabling a paused schedule.
- Six distinct alarm identities/calendar times registered with no resident service. Timely/late/expired/stale/duplicate deliveries, pause during a run, actual Android one-shot callback routing, old callbacks, original-deadline termination and timed-lock cleanup passed.
- Boot/update/clock/timezone recovery paths rearmed alarms without starting a boot dataSync service. Simulated boot identity changes invalidated old monotonic leases. App-open rebuilt future registrations even when stored accepted-registration flags survived simulated OS cancellation.
- Foreground promotion and its timed lock did not wait for a deliberately blocked diagnostic executor. The receiver handoff was released after the service's own lock was held.
- Missing/mismatched Node reports never became successful claims; independent window reasons and Node/source PID plus attempt/service correlation survived report import. A canceled summary became registered again without overwriting confirmed historical success.
- Structured trial retention preserved a fixture on the third calendar date; switching to ordinary mode pruned it. Private UTF-8 Downloads export, live debug controls, native battery-dialog request and unsupported OEM-shortcut fallback passed.
- APK signature verified and signer matched the existing release certificate (`5210e3b887ca7b7128b3057b456d1ea35ee5d46f1776d041665eb64899e1ebab`). APK version metadata verified; update installation succeeded on the emulator. Git whitespace check passed.

## Limits and observed test corrections

No live Interlink API request, credential read, real reward claim, physical reboot, physical Android 14 run, vivo force-stop recovery or 48-hour battery/survival campaign was performed. Termux dispatch is intercepted in Android tests; synthetic correlated reports do not prove the real two-package/API path. Simulated restoration broadcasts are not a physical first-unlock test. No timing or force-stop guarantee follows from emulator success.

One missed-event test initially searched the 12-row dashboard for an older fixture; its assertion was corrected to inspect that execution's structured event. Initial launcher tests stopped before valid swipe verification; the harness was corrected to use supported numeric intent flags and explicit task/launcher transition checks. The final passing runs above use those checks. No failed trial is counted as a passed alarm delivery.

Local evidence: `/tmp/interlink-rc3-build.log`, `/tmp/interlink-rc3-ui-build.log`, `/tmp/interlink-rc3-tests.log`, `/tmp/interlink-rc3-recents.log`, `/tmp/interlink-rc3-doze.log`, `/tmp/interlink-rc3-permission.log`, `/tmp/interlink-rc3-node.log`, plus generated Gradle test/lint reports. Temporary logs and raw user diagnostics are not included in the APK/repository.

[Architecture](../../docs/ADR-ALARM-CLOCK-RC3.md) · [48-hour acceptance standard](../../docs/RC3-ACCEPTANCE.md) · [Installation](../../README-mobile.md).

APK SHA-256: `1d8512abffb188caf52b503cba3449834df0cd30ffad71e1da3959cd275c8c72`.
