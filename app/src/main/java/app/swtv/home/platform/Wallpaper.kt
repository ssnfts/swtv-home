package app.swtv.home.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class Wallpaper(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var bitmap: Bitmap? = null

    fun load(uri: String, done: (Bitmap?) -> Unit) {
        val token = ++generation
        executor.execute {
            val result = try { decode(uri) } catch (_: Exception) { null }
            Handler(Looper.getMainLooper()).post {
                if (token == generation) {
                    bitmap = result
                    done(result)
                } else result?.recycle()
            }
        }
    }



    private fun decode(uri: String): Bitmap? {
        if (uri.isBlank()) return null
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "content")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var sample = 1
        while (options.outWidth / sample > 1920 || options.outHeight / sample > 1080) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        return context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    fun close() {
        generation++
        executor.shutdownNow()
        bitmap = null
    }
}
