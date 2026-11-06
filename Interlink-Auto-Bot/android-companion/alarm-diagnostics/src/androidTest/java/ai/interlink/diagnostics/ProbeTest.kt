package ai.interlink.diagnostics
import android.app.AlarmManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
class ProbeTest {
 private val c get() = InstrumentationRegistry.getInstrumentation().targetContext
 @Before fun setup() { Probe.cancel(c); Probe.prefs(c).edit().clear().commit() }
 @After fun cleanup() { Probe.cancel(c); Probe.prefs(c).edit().clear().commit() }
 @Test fun actualAlarmRecordsDeliveryAndSuppressesDuplicate() {
  val at=System.currentTimeMillis()+3000
  Probe.prefs(c).edit().putString("id","fixture").putLong("expected",at).putLong("expectedElapsed",SystemClock.elapsedRealtime()+3000).commit()
  Probe.manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,Probe.pending(c,"fixture"))
  val end=SystemClock.elapsedRealtime()+15000
  while (!Probe.prefs(c).getString("history","").orEmpty().contains("RECEIVER_DELIVERED") && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
  assertTrue(Probe.prefs(c).getLong("delivered",0)>0)
  val before=Probe.prefs(c).getString("history","")
  ProbeReceiver().onReceive(c,Intent(Probe.ACTION).putExtra("id","fixture"))
  assertEquals(before,Probe.prefs(c).getString("history",""))
 }
 @Test fun cancelledAlarmCannotRecordDeliveryAndReportingDoesNotReplay() {
  Probe.schedule(c,2,"fixture")
  val id=Probe.prefs(c).getString("id","")
  Probe.cancel(c)
  ProbeReceiver().onReceive(c,Intent(Probe.ACTION).putExtra("id",id))
  assertEquals(0L,Probe.prefs(c).getLong("delivered",0))
  Probe.prefs(c).edit().putLong("expected",System.currentTimeMillis()-10000).commit()
  assertTrue(Probe.report(c).contains("No receiver delivery"))
  assertEquals(0L,Probe.prefs(c).getLong("delivered",0))
 }
}
