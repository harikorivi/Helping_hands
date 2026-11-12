package ai.interlink.scheduler

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/** Best-effort navigation only. Private OEM activities are neither permissions nor stable APIs. */
object SetupAccess {
    fun vivo(manufacturer:String=Build.MANUFACTURER,brand:String=Build.BRAND)=listOf(manufacturer,brand).any { it.contains("vivo",true) || it.contains("iqoo",true) }
    fun exact(c:Activity) {
        if(Build.VERSION.SDK_INT<31) return
        try { c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${c.packageName}"))) }
        catch(_:RuntimeException) { info(c) }
    }
    fun info(c:Activity) {
        try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))) }
        catch(e:RuntimeException) { Diagnostics.emit(c,"SETTINGS_NAVIGATION_FAILED",reason=e.javaClass.simpleName) }
    }
    fun oem(c:Activity,kind:String) {
        val choices=if(kind=="autostart") listOf(
            ComponentName("com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity"),
            ComponentName("com.vivo.permissionmanager","com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        else listOf(ComponentName("com.vivo.abe","com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"))
        for(component in choices) {
            val intent=Intent().setComponent(component)
            try {
                val resolved=c.packageManager.resolveActivity(intent,0)
                if(resolved?.activityInfo?.exported!=true) continue
                c.startActivity(intent)
                Diagnostics.emit(c,"OEM_SETTINGS_OPENED",reason=kind,details=JSONObject().put("package",component.packageName)); return
            } catch(_:RuntimeException) { /* Missing/unexported/private firmware component: try the next candidate. */ }
        }
        Diagnostics.emit(c,"OEM_SETTINGS_FALLBACK",reason=kind)
        info(c)
    }
}
