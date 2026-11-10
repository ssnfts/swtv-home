package app.swtv.home.platform
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.Button
import java.util.concurrent.Executors
/** Fixed pixel icons: never retain vendor full-resolution drawables. */
class Icons(private val context:Context) {
 private val cache=LruCache<String,Bitmap>(48)
 private val executor=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 @Volatile private var closed=false
 fun bind(button:Button,pkg:String){cache.get(pkg)?.let{apply(button,it);return};executor.execute{val image=try{val icon=context.packageManager.getApplicationIcon(pkg);Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).also{icon.setBounds(0,0,64,64);icon.draw(Canvas(it))}}catch(_:Exception){null};if(image!=null && !closed){cache.put(pkg,image);handler.post{if(!closed)apply(button,image)}}}}
 private fun apply(button:Button,image:Bitmap){val drawable=BitmapDrawable(context.resources,image);val size=(40*context.resources.displayMetrics.density).toInt();drawable.setBounds(0,0,size,size);button.setCompoundDrawables(null,drawable,null,null);button.compoundDrawablePadding=8}
 fun close(){closed=true;executor.shutdownNow();cache.evictAll()}
}
