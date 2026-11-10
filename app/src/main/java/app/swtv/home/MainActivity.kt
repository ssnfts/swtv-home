package app.swtv.home
import android.app.*
import android.content.*
import android.graphics.drawable.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import app.swtv.home.core.*
import app.swtv.home.platform.*
import app.swtv.home.ui.*
import java.text.DateFormat
import java.util.Date
class MainActivity:Activity() {
 lateinit var config:LauncherConfig;private set
 private lateinit var prefs:Preferences
 val discovery by lazy{Discovery(this)}
 var apps=emptyList<AppEntry>();private set
 var inputs=emptyList<InputEntry>();private set
 private lateinit var weather:Weather
 private lateinit var wallpaper:Wallpaper
 private val icons by lazy{Icons(this)}
 private var home:HomeUi?=null
 private var savedFocus:String?=null
 private val handler=Handler(Looper.getMainLooper())
 private var clock:TextView?=null
 private val ticker=object:Runnable{override fun run(){clock?.text=DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())+"  ·  "+DateFormat.getDateInstance(DateFormat.FULL).format(Date());handler.postDelayed(this,1000)}}
 override fun onCreate(state:Bundle?){super.onCreate(state);prefs=Preferences(this);config=prefs.load();weather=Weather(this);wallpaper=Wallpaper(this);savedFocus=state?.getString("focus")}
 override fun onResume(){super.onResume();refresh()}
 override fun onPause(){savedFocus=home?.focusedKey() ?: savedFocus;handler.removeCallbacks(ticker);weather.cancel();super.onPause()}
 override fun onDestroy(){icons.close();wallpaper.close();weather.close();super.onDestroy()}
 override fun onSaveInstanceState(out:Bundle){out.putString("focus",home?.focusedKey());super.onSaveInstanceState(out)}
 fun save(next:LauncherConfig){config=prefs.save(next);refresh()}
 fun refresh(){savedFocus=home?.focusedKey() ?: savedFocus;apps=discovery.apps(config.showPhoneApps);inputs=discovery.inputs();val ui=HomeUi(this,config);home=ui;val frame=FrameLayout(this);val hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);frame.background=GradientDrawable(GradientDrawable.Orientation.TL_BR,if(ui.light)intArrayOf(0xFFF0F6FC.toInt(),0xFFD9E7F1.toInt()) else if(hour<6 || hour>=20)intArrayOf(0xFF080F22.toInt(),0xFF1C2948.toInt()) else intArrayOf(0xFF10283C.toInt(),0xFF19243C.toInt()));val scroll=ScrollView(this);scroll.addView(ui.root);frame.addView(scroll);setContentView(frame);wallpaper.load(config.wallpaperUri){bmp->if(home===ui && bmp!=null)frame.background=BitmapDrawable(resources,bmp)};clock=ui.text("",28f);ui.root.addView(clock);val status=ui.text("",14f);ui.root.addView(status);weather.refresh(config.weatherEnabled,config.weatherCity){status.text=it};ui.row(listOf(ui.tile("settings","Settings",{SettingsController(this).show()}),ui.tile("system","System Settings",{if(!discovery.launch(Intent(Settings.ACTION_SETTINGS)))notice("System Settings unavailable")}),ui.tile("about","About",{about()})));ui.heading("WATCH & CONNECT");ui.row(listOf(ui.tile("owntv","OwnTV",{if(!discovery.launchPackage(config.iptvPackage))missingOwnTv()}),ui.tile("tvhub","TV Hub",{if(!discovery.launchPackage("app.swtv.tvhub"))notice("TV Hub unavailable","Reserved for app.swtv.tvhub. Install this separate app when available.")}),ui.tile("inputs","Inputs",{chooseInput()})));if(config.modules.isNotEmpty())ui.row(config.modules.mapIndexed{i,m->ui.tile("module:${m.kind}:${m.target}",m.label,{val ok=if(m.kind=="app")discovery.launchPackage(m.target) else discovery.launchInput(m.target);if(!ok)notice("Module unavailable")},{SettingsController(this).moduleMenu(i)})});ui.heading("DOCK · Hold OK for pin / hide");val pins=CatalogRules.dock(apps,config.dock);ui.row((0..5).map{slot->pins.getOrNull(slot)?.let{app->ui.tile("dock:${app.packageName}",app.label,{launch(app)},{appMenu(app)}).also{icons.bind(it as Button,app.packageName)}} ?: ui.tile("slot:$slot","+ Slot ${slot+1}",{SettingsController(this).dockSlot(slot)})});ui.heading("Apps");val visible=CatalogRules.visible(apps,config.hidden);if(visible.isEmpty())ui.root.addView(ui.text("No visible apps · Use Settings to show phone apps or unhide apps"));visible.chunked(config.columns).forEach{row->ui.row(row.map{app->ui.tile("app:${app.packageName}",app.label,{launch(app)},{appMenu(app)}).also{icons.bind(it as Button,app.packageName)}})};ui.root.addView(ui.text("Hold OK on an app to pin, unpin or hide. Hold OK on a custom module to edit.",13f));ui.finish(savedFocus);handler.removeCallbacks(ticker);ticker.run()}
 private fun launch(app:AppEntry){if(!discovery.launchApp(app))notice("App unavailable",app.label+" could not be opened. Refresh or choose another app.")}
 fun appMenu(app:AppEntry){val pinned=app.packageName in config.dock;AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf(if(pinned)"Unpin from dock" else "Pin to dock","Hide app")){_,which->if(which==0)save(config.copy(dock=if(pinned)config.dock-app.packageName else CatalogRules.movePin(config.dock,app.packageName,config.dock.size))) else save(config.copy(hidden=config.hidden+app.packageName,dock=config.dock-app.packageName))}.show()}
 fun chooseInput(){if(inputs.isEmpty()){notice("Inputs unavailable","No TV inputs were reported by this device.");return};AlertDialog.Builder(this).setTitle("Inputs").setItems(inputs.map{it.label}.toTypedArray()){_,i->if(!discovery.launchInput(inputs[i].id))notice("Input unavailable")}.show()}
 fun missingOwnTv(){AlertDialog.Builder(this).setTitle("OwnTV unavailable").setMessage("${config.iptvPackage} is not installed or cannot launch. Open the project releases in your browser or choose an installed fork. No APK is downloaded automatically.").setPositiveButton("Project releases"){_,_->if(!discovery.launch(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Thedurancode/owntv/releases"))))notice("No browser available")}.setNeutralButton("Choose installed app"){_,_->SettingsController(this).iptv()}.setNegativeButton("Cancel",null).show()}
 fun notice(title:String,message:String=title){AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show()}
 private fun about(){notice("SWTV Home · 0.1.0","Original native TV launcher. OwnTV is a separate application. Weather: Open-Meteo, enabled only by you. Update release feed is not configured until Phase 4.")}
 fun pickWallpaper(){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),41)}catch(_:Exception){notice("Image picker unavailable","Use the built-in time-based gradient on this device.")}}
 @Deprecated("Platform activity result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==41 && resultCode==RESULT_OK){data?.data?.let{uri->try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);save(config.copy(wallpaperUri=uri.toString()))}catch(_:Exception){notice("Image unavailable","The image provider did not grant persistent access.")}}}}
}
