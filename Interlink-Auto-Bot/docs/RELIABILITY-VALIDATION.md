# Reliability validation and release gate — 1.7-rc1

**Production acceptance: NOT MET. Physical-phone root cause: OPEN.** No live claim was submitted during development. The phone could not be reached through USB or wireless ADB. Four-hour/overnight physical scenarios cannot be honestly marked passed from short emulator tests. This candidate supplies evidence and fixes confirmed gaps; it is not a claimed permanent fix.

## T1–T18 matrix

“Partial” means a component was executed, not that the full real-phone scenario passed. Tests use mocked HTTP responses where stated, never the user's live credentials. Logs below are local development artifacts; no sensitive logs are committed.

| ID | Scenario and expected behavior | Actual evidence/status | Failure root cause / remaining limitation |
|---|---|---|---|
| T1 | App open: register, deliver, dispatch, independent window results | Partial: emulator receiver/recovery/callback tests; Node mocked claim/verification tests pass | Full real Termux + live API run not executed |
| T2 | Background: receiver/service starts without UI | Candidate physical validation pending; previous v1.6 emulator lifecycle evidence is historical only | No candidate physical proof |
| T3 | Removed from Recents: receiver and execution before reopening | Physical candidate pending; supplied standalone probe delivered before reopening, not this full chain | OEM behavior unresolved |
| T4 | Screen locked 4+ hours: due alarm and original-deadline execution | NOT RUN on candidate physical phone; 4h no-claim probe added | Release blocker |
| T5 | Saver enabled: record condition and actual latency/outcome | NOT RUN on candidate physical phone | Saver at old report time does not establish state at deadline |
| T6 | Saver disabled: comparable long-idle run | NOT RUN on candidate physical phone | Required paired comparison with T5 |
| T7 | Doze: supported alarm delivery; no expired claim replay | Separate emulator forced-idle test to be recorded in release artifact; physical pending | Emulator cannot reproduce Funtouch policies |
| T8 | Overnight: multiple due runs complete or explain failures | NOT RUN; 8h no-claim probe added, not a substitute for scheduled claims | Release blocker |
| T9 | Reboot: rearm future slots, no unauthorized boot dataSync FGS | Partial: reboot-receiver invocation integration test passes | Real reboot/unlock plus Termux not executed on candidate |
| T10 | Network disconnected: bounded requests and safe stage-specific error | Mocked offline request tests pass | Physical network interruption/restore not executed |
| T11 | Expired access: refresh once, persist rotated pair, continue | Automated auth tests pass with fake server | Live token rotation not triggered for testing |
| T12 | Invalid refresh: fail closed, no OTP prompt, explicit reason | Automated rejected-refresh tests pass | Physical account not modified |
| T13 | Termux killed: report internal error/missing callback as unknown; no duplicate POST | Partial: missing correlated report test and Node submitted-marker tests pass | Actual Termux process-kill E2E not executed |
| T14 | Alarm permission revoked: record unavailable state, reconcile on grant | Source audit only for candidate; not executed | Revocation may stop app/cancel alarms; battery exemption can mask toggle changes |
| T15 | Timezone changed: rebuild local slots, cancel obsolete slots, no late claim | Partial: receiver and local-date/DST unit tests pass | Physical timezone transition not executed |
| T16 | Duplicate delivery/recovery: preserve other slots and no repeated POST | Emulator duplicate/recovery tests + Node persisted-submission tests pass | Cross-app live concurrency not executed |
| T17 | Force-stop: no execution guarantee; reopen restores future schedule | NOT RUN on candidate; expected Android limitation, not a bypass target | Must never count force-stop suppression as successful background delivery |
| T18 | OEM restrictions: matched settings and long-idle comparisons | Existing iQOO 17:26 miss documented; candidate NOT RUN | Root cause remains unknown; no privileged alarm trace |

## Automated evidence

The release validation artifact records the final actual counts and build/lint results. Node test output: `/tmp/interlink-rc-node.log`. Android build: `/tmp/interlink-rc-build.log`. Instrumentation: `/tmp/interlink-rc-instrument.log` (final reruns may use a `-final` suffix). Tests include safe report redaction/correlation, partial report handling, missing report != success, receiver entry before disabled guard, preservation of future slots, late/duplicate delivery and foreground recovery with original timestamps.

## Real-device campaign (required, not performed by the development Mac)

1. Upgrade both bot and candidate APK, preserve credentials and ledger. Record model, Android/Funtouch versions, exact capability and both app exemptions. Do not reset existing claim state.
2. For no-claim preliminary T4/T8 probes, choose the 4h/8h button, note Home vs swipe and intended time, and do not open the app before the deadline. Only one diagnostic probe can be pending. Avoid reserved scheduled periods and document deferred/busy tests separately.
3. Run the actual six-slot schedule for at least 48 hours (12 scheduled slots), including two nights, with no diagnostic probes competing for idle-alarm quota. First day saver off; second day use the user's intended saver setting. Record when saver settings change. Do not disable battery/security services.
4. Collect **Copy structured execution report** and the corresponding private claim log after the run. Collect at least daily because detailed logs retain today/yesterday. Record physical UI/screen actions; absence of an event cannot identify an OS root cause.
5. For each due execution ID, pair ALARM_SCHEDULED, RECEIVER_STARTED, ALARM_RECEIVED, TERMUX_DISPATCHED, NODE_PROCESS_STARTED, both window-completion events, exit and callback. Preserve uncertain submissions and never retry them simply for test coverage.
6. For destructive lifecycle scenarios use a no-claim probe or a disposable emulator. Do not deliberately interrupt a live claim submission. Test force-stop separately as an expected restriction; reopen explicitly afterward.
7. Compute exported-event statistics with `node tools/analyze-diagnostics.js copied-report.txt`. This reports unique due registered slots, receiver deliveries, no-delivery-evidence count and min/mean/p95/max latency. It excludes probes/cancelled/future slots and does not count foreground recovery as delivery. Retained-event statistics are not lifetime reliability.

## Acceptance criteria

- All 12 due slots accounted for with evidence, no unexplained missing receiver/dispatch/callback. Report delivery rate and latency distribution; do not hide misses as recoveries.
- Node startup early enough to permit each original claim window, with no requests after its deadline. Actual two-claim success required when the server independently reports eligibility in both windows. Ineligible results are explicit, not technical failures or invented successes.
- Refresh rotation, duplicate protection and uncertain-outcome handling demonstrated by automated tests and corroborated by safe live logs where naturally exercised.
- T4/T8 pass on the affected device; T5/T6 differences documented. Required unexecuted tests remain blockers, not “passed by design.”
- No continuous cron/Node/monitoring service. Bounded execution locks released; power observer exists only while process lives.
- Any recurring device-specific missing delivery after this campaign prevents a production-ready claim. Recommend an always-on Linux timer host for stricter scheduling; native Android migration addresses Termux dispatch only, not absent OS delivery.

## Current measurable field evidence

The supplied v1.6 17:26 slot has one registration and zero recorded deliveries before 18:03 reconciliation: **0/1 recorded delivery**, not proof the receiver never ran. Independent diagnostic samples with screen non-interactive show approximately 32.24s and 72.62s delay; they are not scheduler success statistics. There are **no candidate real-device multi-hour reliability statistics yet**.
