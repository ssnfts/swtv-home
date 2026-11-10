package app.swtv.updates

data class BuildIdentity(val applicationId: String, val versionName: String, val versionCode: Long, val buildType: String)
sealed interface UpdateState { data object Unconfigured : UpdateState }
fun interface UpdateProvider { fun state(): UpdateState }
class UnconfiguredUpdateProvider : UpdateProvider {
    override fun state(): UpdateState = UpdateState.Unconfigured
}
object UpdateReport {
    fun format(identity: BuildIdentity, state: UpdateState): String = buildString {
        appendLine(identity.applicationId)
        appendLine("Version: ${identity.versionName} (${identity.versionCode})")
        appendLine("Build: ${identity.buildType}")
        when (state) {
            UpdateState.Unconfigured -> append("Release updates are not configured.")
        }
    }
}
