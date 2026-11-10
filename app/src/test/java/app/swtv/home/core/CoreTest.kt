package app.swtv.home.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private val alpha = AppEntry("org.alpha", "Main", "Alpha")
    private val beta = AppEntry("org.beta", "Main", "beta")

    @Test fun configurationRoundTripsEverySetting() {
        val config = LauncherConfig("dark", "violet", 8, true, false,
            listOf("org.beta", "org.alpha"), setOf("org.hidden"),
            listOf(ModuleTile("OwnTV", "tv.own.owntv", "app"), ModuleTile("HDMI", "org.input/.Service/HW1", "input")),
            "content://images/42", "Sydney", true, "org.iptv")
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
        assertTrue(ConfigCodec.encode(config).contains("schemaVersion"))
    }
    @Test fun malformedAndFutureDocumentsUseSafeDefaults() {
        for (text in listOf("not JSON", "[]", "null", "{\"schemaVersion\":2,\"columns\":9}", "{\"schemaVersion\":\"1\",\"columns\":9}")) {
            assertEquals(LauncherConfig(), ConfigCodec.decode(text))
        }
    }
    @Test fun invalidFieldsFallBackIndependently() {
        val config = ConfigCodec.decode("""{"schemaVersion":1,"theme":"bogus","accent":false,"columns":"8","reducedMotion":"true","showPhoneApps":false,"weatherEnabled":3,"weatherCity":"Sydney","iptvPackage":"","dock":["org.alpha","org.alpha","app.swtv.home","",12],"hidden":["org.beta",9]}""")
        assertEquals(LauncherConfig(showPhoneApps=false, weatherCity="Sydney", dock=listOf("org.alpha"), hidden=setOf("org.beta")), config)
    }
    @Test fun importedCollectionsAndColumnsAreBounded() {
        val config = LauncherConfig(columns=100, dock=(1..30).map { "org.app$it" },
            modules=(1..30).map { ModuleTile("App $it", "org.app$it", "app") })
        val decoded = ConfigCodec.decode(ConfigCodec.encode(config))
        assertEquals(8, decoded.columns)
        assertEquals(6, decoded.dock.size)
        assertEquals(8, decoded.modules.size)
        assertEquals(3, ConfigCodec.decode("""{"schemaVersion":1,"columns":-10}""").columns)
    }
    @Test fun unsafeModuleTargetsAreRejected() {
        val modules = listOf(ModuleTile("ok", "org.alpha", "app"), ModuleTile("HDMI", "org.input/.Service/HW1", "input"),
            ModuleTile("bad", "javascript:alert(1)", "url"), ModuleTile("file", "file:///etc", "url"),
            ModuleTile("self", "app.swtv.home", "app"), ModuleTile("blank", "", "app"),
            ModuleTile("unknown", "org.alpha", "unknown"), ModuleTile("", "org.alpha", "app"))
        assertEquals(modules.take(2), ConfigCodec.decode(ConfigCodec.encode(LauncherConfig(modules=modules))).modules)
    }
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
    @Test fun animationsRespectUserPreferenceAndLowMemory() {
        assertTrue(CatalogRules.animationsEnabled(false,false))
        assertFalse(CatalogRules.animationsEnabled(true,false))
        assertFalse(CatalogRules.animationsEnabled(false,true))
        assertFalse(CatalogRules.animationsEnabled(true,true))
    }
}

