# Task-removal recovery review — 12 November 2026

Source: the user's 18:34 diagnostic export, 143 retained structured events. Raw diagnostics, credentials and account data are not included in the repository. Times below use Asia/Kolkata.

## Supported findings

- All 143 retained Android device samples report Scheduler `batteryExempt=false`. This describes sampled Android exemption state, not every unsampled instant. Battery Saver is true in many samples and false in others. Neither flag proves the cause of a particular OEM termination.
- Android's retained exit description records task removal for the old running process. About 1.2 seconds later a new process records `PROCESS_STARTED` and `SERVICE_TASK_REMOVED`, with no intervening `FOREGROUND_PROMOTED` or `SCHEDULER_SERVICE_STARTED`. That is a concrete recovery gap: a callback-only service instance can exist without the normal started-service initialization.
- A separate retained exit explicitly names `com.vivo.abe` as the force-stop requester. That record does not establish the component's decision rule or prove that absent foreground state caused the stop.

For example, the old process exit occurred at **12 Nov 14:38:35.499**, followed by the new process's removal callback at **14:38:36.707**. A separate sequence records a removal callback at **11 Nov 14:10:55.942** and `onDestroy` at **12 Nov 01:41:32.771**—more than 11 hours later. Treating those as immediate back-to-back teardown is incorrect.

`PROCESS_EXIT_OBSERVED.actualAt` is when history was read; `details.exitAt` is the historical exit time. `details.pid` identifies the exited process, whereas the device snapshot identifies the observer. Old `onDestroy_received` was recorded after the implementation cleared its running flag, so a false flag there cannot prove when or why foreground state was lost. Android device snapshots are asynchronous and carry their own sampling time.

## Corrections to the proposed diagnosis

Version 2.0-rc1 already returned `START_STICKY` and declared `stopWithTask=false`. Its `onTaskRemoved` implementation logged the callback; it did not call `stopSelf`, release the wake lock or set `running=false`. Android does not require a healthy started service to restart itself for every Recents swipe. [Service lifecycle contract](https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent)).

The confirmed application-owned gap is narrower: when the framework/OEM creates a service solely for the callback, `onStartCommand` may be absent in the recorded lifecycle, leaving that instance unpromoted. Version 2.0-rc2 requests a foreground start only when scheduling remains enabled and the current instance has not been promoted. A healthy instance keeps running and checks its timer; a paused instance stays paused. Repeated callback-only recovery requests are coalesced. Background-start rejection is logged; no restart alarm loop or force-stop bypass is claimed.

## Implemented changes

- Guarded callback-only foreground-start recovery, preserving original session leases, accepted slot markers and Node duplicate barriers.
- `SERVICE_CREATED`, service instance IDs, PID-at-event, foreground-start flags, and callback-time running/wake/enabled fields. These are distinct from later power sampling.
- `SERVICE_STOP_REQUESTED` with explicit app-owned stop reasons. `onDestroy` without such a request is labeled `framework_destroy_no_app_stop_request`, without inventing a particular OS requester.
- A user-triggered **Request Scheduler battery exemption** button invokes `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for this app, with the matching manifest permission and a settings-list fallback. The grant is checked again from Android; opening the dialog never counts as approval. Termux remains a separate app with separate battery settings. [Android exemption guidance](https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases).
- Visible warnings when native Scheduler exemption is missing or Battery Saver is on. There is no programmatic attempt to turn Battery Saver off or change OEM/system-package policy.

This personal sideloaded build requests direct exemption because Doze can break its timing behavior. It remains user-approved special access, not an OEM whitelist or protection against true force-stop. The lint policy warning is retained; eligibility for public-store distribution is not established and needs a separate review.

## Acceptance boundary

[Release validation](../android-companion/releases/VALIDATION-2.0-rc2.md) distinguishes executed emulator tests from physical acceptance. A framework-created bound-only service is used to exercise callback-only initialization without manually constructing an unattached Service. That tests the recovery code; it does not reproduce vivo's private task-removal implementation or prove immunity from its later force-stop.
