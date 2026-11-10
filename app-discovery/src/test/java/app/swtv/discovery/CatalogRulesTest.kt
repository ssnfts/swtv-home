package app.swtv.discovery

import org.junit.Assert.*
import org.junit.Test

class CatalogRulesTest {
    private val alpha = AppEntry("org.alpha", "Main", "Alpha")
    private val beta = AppEntry("org.beta", "Main", "beta")

    @Test fun catalogHasOneEntryPerPackageAndStableLabelOrder() {
        val entries = listOf(beta, alpha, alpha.copy(className="Other"), AppEntry("app.swtv.home", "Main", "Home"), AppEntry("org.aardvark", "Main", "Alpha"))
        assertEquals(listOf(AppEntry("org.aardvark", "Main", "Alpha"), alpha, beta), CatalogRules.deduplicate(entries))
    }

    @Test fun dockDropsStaleDuplicateAndSelfPinsAndHonorsLimit() {
        assertEquals(listOf(beta, alpha), CatalogRules.dock(listOf(alpha,beta), listOf("gone", "org.beta", "org.beta", "app.swtv.home", "org.alpha")))
        assertEquals(listOf(beta), CatalogRules.dock(listOf(alpha,beta), listOf("org.beta","org.alpha"),1))
        assertTrue(CatalogRules.dock(listOf(alpha),listOf("org.alpha"),-1).isEmpty())
    }

    @Test fun hiddenAppsAreRemovedWithoutDisturbingCatalogOrder() {
        assertEquals(listOf(alpha), CatalogRules.visible(listOf(beta,alpha),setOf("org.beta")))
    }

    @Test fun movingPinsClampsPositionAndRemovesDuplicates() {
        assertEquals(listOf("org.beta","org.alpha"), CatalogRules.movePin(listOf("org.alpha","org.beta","org.alpha"),"org.beta",-5))
        assertEquals(listOf("org.alpha","org.beta","org.new"), CatalogRules.movePin(listOf("org.alpha","org.beta"),"org.new",99))
        assertEquals(listOf("org.alpha"),CatalogRules.movePin(listOf("org.alpha"),"app.swtv.home",0))
    }
}
