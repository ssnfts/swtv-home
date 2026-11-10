package app.swtv.home.platform
import android.content.*
import android.media.tv.*
import app.swtv.home.core.*
data class InputEntry(val id:String,val label:String)
class Discovery(private val context:Context) {
 fun apps(phone:Boolean):List<AppEntry> = CatalogRules.deduplicate((listOf(Intent.CATEGORY_LEANBACK_LAUNCHER)+if(phone) listOf(Intent.CATEGORY_LAUNCHER) else emptyList()).flatMap { category -> context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category),0).map { AppEntry(it.activityInfo.packageName,it.activityInfo.name,it.loadLabel(context.packageManager).toString()) } })
 fun inputs():List<InputEntry> = try { (context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.map { InputEntry(it.id,it.loadLabel(context).toString()) } } catch(_:Exception){emptyList()}
 fun launchApp(app:AppEntry):Boolean=launch(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(app.packageName,app.className)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
 fun launchPackage(pkg:String):Boolean { val pm=context.packageManager; val intent=pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false; return launch(intent) }
 fun launchInput(id:String)=launch(Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(id)))
 fun launch(intent:Intent):Boolean=try { context.startActivity(intent); true } catch(_:Exception){false}
}
