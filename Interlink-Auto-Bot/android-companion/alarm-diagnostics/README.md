# Alarm Delivery Check — offline diagnostic app

Install [Alarm-Delivery-Check-1.0.apk](../releases/Alarm-Delivery-Check-1.0.apk) on your phone. This is a separate app (`ai.interlink.diagnostics`) and does not replace Interlink Scheduler. No USB, wireless pairing, internet connection, account, Termux access or root is required. It never calls the Interlink API or submits a claim.

## Use on the affected phone

1. Open **Alarm Delivery Check**. Grant exact-alarm access if needed. For an equivalent comparison, use its Battery optimization button and exempt **Alarm Delivery Check** just as you did Interlink Scheduler. Manufacturer-specific Autostart settings are separate and not detected by this app.
2. Select **Home + screen locked**, then **Schedule test in 2 minutes**. Press Home and lock the screen.
3. Reopen at least one minute after the displayed expected time. Tap **Copy diagnostic report** and paste the report into the support conversation.
4. Wait at least ten minutes between tests to reduce idle-alarm throttling. Select **Swiped from Recents + screen locked**, schedule another test, then swipe this diagnostic app away and lock the screen. Reopen one minute after the expected time and copy the new report.
5. Use **Schedule test in 10 minutes** for a longer idle comparison. Only one test is pending at a time; scheduling another replaces it. A short test does not demonstrate overnight reliability.

Do not force-stop the app. Reboot invalidates the pending test; schedule a new one afterward. Opening or refreshing the app never repairs, reschedules or manually invokes the alarm. Cancelling a pending test does not erase its history. History is capped at 150 entries, with the latest 30 displayed; the copied report includes all retained entries.

## Interpret the report

- `REGISTERED`: Android accepted the exact RTC_WAKEUP alarm request. This does not prove delivery.
- `APP_LEFT_FOREGROUND`: the activity paused. It cannot distinguish Home from swiping; the selected scenario is your annotation.
- `RECEIVER_DELIVERED`: the actual OS receiver ran. It includes wall-clock and elapsed-time delay and power state at delivery.
- `APP_OPENED`: when the app returned to the foreground. Compare this with delivery. Delivery near reopening is not evidence of unattended success.
- No delivery recorded: the receiver has not written evidence; it does not identify which OS restriction caused the delay.
- `SYSTEM_EVENT`: clock/timezone/update/reboot signals, useful when interpreting wall-clock changes.

Exact-alarm capability can come from power-save exemption rather than a separately granted Settings toggle. The app does not change system security controls itself.

## Limits

This app can test only its own package's alarm behavior. It cannot inspect another app's private preferences, Android's privileged alarm queue, or system-wide logs. A successful test here does not prove Interlink Scheduler or Termux execution works. If this independent app also receives alarms only upon reopening, that supports a broader device/background-management issue, but does not establish the precise cause. Reports include device model, Android version, timezone and timestamps, with no credentials or account data.

## Development

From `android-companion`:

```sh
gradle :alarm-diagnostics:testDebugUnitTest :alarm-diagnostics:assembleDebug :alarm-diagnostics:assembleDebugAndroidTest :alarm-diagnostics:lintDebug
```

Instrumented tests are for disposable emulators only; they clear this diagnostic app's preferences. See the release validation report for actual tests and limitations.
