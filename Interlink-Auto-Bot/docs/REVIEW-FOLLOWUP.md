# Review follow-up — 1.8-rc3

The supplied review contains useful suggestions but does not establish the physical root cause.

| Review claim | Assessment |
|---|---|
| finishAndRemoveTask definitely caused the observed exit | Unproven. Latest field report has MONITOR_TASK_REMOVED for an actual swipe, followed by a successful timer, then termination later. No APP_CLOSE_REQUESTED identified that button as the cause. Change the app button to moveTaskToBack(true) to support a comparison without removing its task. |
| Android 14 requires an intent filter for our explicit alarm broadcasts | Incorrect for this case. The receivers have no filters and the app uses explicit PendingIntents; Android documents exemptions for components without filters, same-app and system-originated intents. Existing device logs also contain actual successful receiver deliveries. |
| Six slots collide despite distinct request codes | Not demonstrated. Android includes the PendingIntent request code in identity. Our 101/105/109/113/117/121 codes distinguish the slots. Extras alone would not; the review correctly notes that general rule. Adding data URIs would change cancellation identities and requires migration without evidence of this defect. |
| Autostart/Associated startup is definitely disabled | Unknown. AOSP battery-exemption status cannot establish proprietary settings; ask for their exact displayed values. |
| e.subReason is a public Android 31+ API | Incorrect. It is absent from the public API 35 android.jar checked with javap. Do not use reflection or hidden API assumptions. |
| e.description may help | Correct. Public since API 30, nullable and format-unstable. Capture bounded redacted text and numeric reason/status/importance/PSS/RSS. It is not guaranteed to name an OEM daemon or expose an internal subreason. |

The new exit-details cursor rereads up to eight retained historical process exits once after upgrade, so additional metadata may be available without generating a new system bug report or reproducing a failure. Retention depends on Android. No source/token update is required if already on 1.8-rc2.

Changes: Background app, keep scheduling now calls moveTaskToBack(true), keeping its task in Recents. This is a deliberate operational option, not a claim that arbitrary swiping is now reliable. Service restart and wake-lock policy are unchanged.

References:

- https://developer.android.com/guide/components/intents-filters
- https://developer.android.com/reference/android/app/PendingIntent
- https://developer.android.com/reference/android/app/ApplicationExitInfo
