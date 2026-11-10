package app.swtv.home.platform
import android.content.Context
import android.os.*
import org.json.JSONObject
import java.net.*
import java.util.concurrent.Executors
class Weather(context:Context) {
 private val prefs=context.getSharedPreferences("weather-cache",Context.MODE_PRIVATE)
 private val executor=Executors.newSingleThreadExecutor()
 private var generation=0
 @Volatile private var connection:HttpURLConnection?=null
 fun refresh(enabled:Boolean,city:String,done:(String)->Unit){val token=++generation; if(!enabled){done("Weather off · Enable in Settings");return}; val same=prefs.getString("city","")==city; val last=if(same)prefs.getLong("attempt",0) else 0; val cached=if(same)prefs.getString("display",null) else null; if(!RuntimePolicy.shouldFetch(enabled,city,System.currentTimeMillis(),last)){done(cached ?: "Weather unavailable · retry after 30 minutes");return}; prefs.edit().putString("city",city).putLong("attempt",System.currentTimeMillis()).apply(); done(cached?.plus(" · refreshing") ?: "Loading weather…"); executor.execute { val display=try { val geo=get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="+URLEncoder.encode(city,"UTF-8")).optJSONArray("results")?.optJSONObject(0) ?: error("City not found"); val forecast=get("https://api.open-meteo.com/v1/forecast?latitude=${geo.getDouble("latitude")}&longitude=${geo.getDouble("longitude")}&current=temperature_2m"); "${geo.getString("name")} · ${forecast.getJSONObject("current").getDouble("temperature_2m")}°C · Open-Meteo" }catch(_:Exception){cached?.plus(" · stale / unavailable") ?: "Weather unavailable · Open-Meteo"}; Handler(Looper.getMainLooper()).post { if(token==generation){prefs.edit().putString("display",display).apply();done(display)} } } }
 private fun get(url:String):JSONObject { require(RuntimePolicy.allowedUrl(url)); val c=URL(url).openConnection() as HttpURLConnection; connection=c; try { c.connectTimeout=6000;c.readTimeout=6000;c.instanceFollowRedirects=false; require(c.responseCode==200); val bytes=c.inputStream.use { input -> val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096); while(true){ val n=input.read(buffer);if(n<0)break;require(out.size()+n<=131072);out.write(buffer,0,n)};out.toByteArray() }; return JSONObject(String(bytes,Charsets.UTF_8)) } finally { c.disconnect();connection=null } }
 fun cancel(){generation++;connection?.disconnect()}
 fun close(){cancel();executor.shutdownNow()}
}
