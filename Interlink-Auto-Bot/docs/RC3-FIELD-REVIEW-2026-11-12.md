# rc3 physical probe review and rc4 pending-alarm correction

Source: the user's 12 November 21:58 export. Raw diagnostics are not published. Times below are phone-local IST.

## Verified phone results

The 21:54 probe ran in a new Android process after the previously recorded process exited with a task-removal description. Receiver delay was 8,780ms. Service entry followed 219ms later; foreground promotion followed another 10ms later. The probe completed and released its service and wake lock. The observed screen was off and device locked. Sampled `idle=false` does not establish whether deep Doze occurred before delivery; this is a successful cold, locked-screen alarm/FGS probe, not proof of a physical deep-Doze or complete Node claim session.

Native Scheduler and Termux battery exemptions, exact-alarm capability and standby bucket 5 are present in the current snapshot. The probe explicitly launched no Node or claim. No scheduled Node startup was recorded in the rc3 portion of this export.

A later retained exit description names com.vivo.abe as force-stop requester for the idle probe process at 21:57:26.833. At 21:58:18.651 the UI opened; the app then issued six fresh alarm registrations. The export therefore does not show whether the previous alarms survived that stop. Its accepted-registration flags cannot establish force-stop immunity or a Recents-lock exception. There was no OS alarm-registry capture between that stop and the manual reopening.

## Application-owned 21:26 race

The 21:26 alarm was accepted at 21:25:29.182 and re-registered at 21:25:59.499. App-open at 21:26:01.960 ran reconciliation, which recorded the slot missed at 21:26:02.038 and replaced it with tomorrow's slot. The original alarm was already armed before its due time. Describing this only as installation/setup finishing too late is incorrect: restoration could prematurely invalidate a still-pending delivery during its legitimate original window. This is an app-owned defect, irrespective of the manufacturer's original delivery delay.

## rc4 correction

For app-open, manual repair and alarm-triggered reconciliation, an already-armed undelivered slot within its original eight-minute session retains its timestamp and generation. `ALARM_RECONCILIATION_DEFERRED` records that the app is waiting for the real receiver. Reconciliation does not itself start a claim. Expired/unarmed slots are still skipped without replay, and reboot/update/permission or clock-change restoration remains distinct.

After a real alarm starts its bounded work and rearms its own hour, a short recovery task also repairs independently expired/missing slots. There is no resident scheduling loop or between-slot wake lock. All accepted markers, original deadlines and Node duplicate barriers remain intact.

Preserving a generation does not prove that a pending OS alarm still exists after an OEM stop; if the platform already canceled it, this guard cannot revive that original delivery. Future-alarm restoration and physical acceptance remain necessary. The correction prevents our own premature replacement of a legitimate pending alarm.

## Overnight verification

Use rc4 with the currently verified native access. Keep one labeled operating condition: Home/locked screen, optionally task-locked in Recents; no repeated opening, manual claims or cleaners. Record any call, unlock or intervention. Inspect after **09:35 on 13 November**, so the 09:31–09:34 second window has ended. Audit 01:26, 05:26 and 09:26 receiver, Node, both independent windows and resource-release events. Recents locking is a test condition, not a guarantee against force-stop.
