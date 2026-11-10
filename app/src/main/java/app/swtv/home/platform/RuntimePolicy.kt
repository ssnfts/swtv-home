package app.swtv.home.platform
object RuntimePolicy {
 fun effects(reduced:Boolean,lowRam:Boolean,system:Boolean)=!reduced && !lowRam && system
 fun shouldFetch(enabled:Boolean,city:String,now:Long,last:Long)=enabled && city.isNotBlank() && (last==0L || now-last>=1800000L)
 fun allowedUrl(value:String):Boolean=try { val u=java.net.URI(value); u.scheme=="https" && u.host in setOf("api.open-meteo.com","geocoding-api.open-meteo.com") && u.userInfo==null } catch(_:Exception){false}
 fun focus(saved:String?,keys:List<String>)=saved?.takeIf{it in keys} ?: keys.firstOrNull()
}
