package app.swtv.home.platform
import android.content.Context
import android.graphics.*
import android.net.Uri
import java.util.concurrent.Executors
class Wallpaper(private val context:Context) {
 private val executor=Executors.newSingleThreadExecutor()
 private var generation=0
 private var bitmap:Bitmap?=null
 fun load(uri:String,done:(Bitmap?)->Unit) { val token=++generation; executor.execute { val result=try { if(uri.isBlank()) null else { val parsed=Uri.parse(uri); require(parsed.scheme=="content"); val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}; context.contentResolver.openInputStream(parsed)?.use{BitmapFactory.decodeStream(it,null,options)}; var sample=1; while(options.outWidth/sample>1920 || options.outHeight/sample>1080) sample*=2; options.inJustDecodeBounds=false; options.inSampleSize=sample; context.contentResolver.openInputStream(parsed)?.use{BitmapFactory.decodeStream(it,null,options)} } }catch(_:Exception){null}; android.os.Handler(android.os.Looper.getMainLooper()).post { if(token==generation){bitmap=result;done(result)}else result?.recycle() } } }
 fun close(){generation++; executor.shutdownNow(); bitmap=null}
}
