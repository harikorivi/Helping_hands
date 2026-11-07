# Public exit metadata and backgrounding validation — 1.8-rc3

2026-11-07. Version code 14. Node, alarm routing, service type and wake-lock policy unchanged.

- Build/assemble/lint passed: 0 errors, 43 warnings.
- 11 JVM tests passed, including two new tests preserving a useful system cause while redacting token/password/Bearer/URL/email values, bounding long descriptions and handling null/unknown metadata.
- APK signature verification passed. SDK API 35 public ApplicationExitInfo methods were inspected with javap: getDescription exists, getSubReason does not.
- Android documentation verified that components without filters and same-app/system intents are exempt from the disputed intent-matching rule, and different PendingIntent request codes distinguish identities. Manifest/identity changes were not made based on incorrect review claims.
- UI backgrounding change inspected and compiled; no new emulator UI or physical Funtouch test was run for this small change. Prior task-removal tests belong to 1.8-rc2.
- System exit descriptions may be unavailable, generic, unstable or no longer retained. The new cursor rereads at most eight retained own-app records once after upgrading. No hidden-API/reflection usage. Historical cause remains unresolved until new evidence exists.

Local build evidence: `/tmp/interlink-exit-details-build.log` and app/build/test-results/testDebugUnitTest. No live claims.

SHA-256: `a88fe8869bf2ffa2ec49d40906f2936d7af2afe5c77dfba806b17931e5168f0a`.
