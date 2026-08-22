package com.safa.account.ui.localization

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocalizationContractTest {
    private fun rootFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Repository file not found: $path")
    }

    @Before
    fun initializeCatalog() {
        AndroidStringCatalog.initialize(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `logical keys resolve through English and Bangla Android resources`() {
        assertEquals("Dashboard", AndroidStringCatalog.get("EN", "dashboard"))
        assertEquals("ড্যাশবোর্ড", AndroidStringCatalog.get("BN", "dashboard"))
        assertEquals("unknown_key", AndroidStringCatalog.get("EN", "unknown_key"))
    }

    @Test
    fun `English and Bangla generated resource catalogs stay complete and symmetric`() {
        val english = rootFile("app/src/main/res/values/ui_strings.xml").readText()
        val bangla = rootFile("app/src/main/res/values-bn/ui_strings.xml").readText()
        val keyPattern = Regex("name=\"([^\"]+)\"")
        val englishKeys = keyPattern.findAll(english).map { it.groupValues[1] }.toSet()
        val banglaKeys = keyPattern.findAll(bangla).map { it.groupValues[1] }.toSet()

        assertEquals(englishKeys, banglaKeys)
        assertTrue("Expected the migrated product catalog, found only ${englishKeys.size} keys", englishKeys.size > 500)
        assertTrue("Logical compatibility registry unexpectedly shrank", AndroidStringResources.ids.size > 500)
        assertTrue(
            "Every logical compatibility entry must point at a generated Android resource ID",
            AndroidStringResources.ids.values.all { it != 0 },
        )
    }

    @Test
    fun `core static copy no longer uses direct English Bangla sentence branches`() {
        val coreFiles = listOf(
            "app/src/main/java/com/safa/account/MainActivity.kt",
            "app/src/main/java/com/safa/account/ui/screens/LoginScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/DashboardScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/CustomerScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/SupplierScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/TransactionScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/WalletScreen.kt",
            "app/src/main/java/com/safa/account/ui/screens/SettingsScreen.kt",
        )
        val directLiteralBranch = Regex(
            "if\\s*\\(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*==\\s*\"BN\"\\s*\\)\\s*\"(?:\\\\.|[^\"])*\"\\s*else\\s*\"(?:\\\\.|[^\"])*\""
        )

        coreFiles.forEach { path ->
            val source = rootFile(path).readText()
            assertFalse("Direct locale sentence branch remains in $path", directLiteralBranch.containsMatchIn(source))
        }

        val viewModel = rootFile("app/src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()
        assertFalse(viewModel.contains("val bnMap = mapOf"))
        assertFalse(viewModel.contains("val enMap = mapOf"))
        assertTrue(viewModel.contains("AndroidStringCatalog.get(lang, key)"))
    }

    @Test
    fun `login language selector keeps minimum accessible touch target and decorative icons stay silent`() {
        val login = rootFile("app/src/main/java/com/safa/account/ui/screens/LoginScreen.kt").readText()
        assertTrue(login.contains("Modifier.heightIn(min = 48.dp).testTag(\"auth_lang_toggle\")"))
        assertTrue(login.contains("Icon(Icons.Default.Phone, contentDescription = null)"))
        assertTrue(login.contains("Icon(Icons.Default.Security, contentDescription = null)"))
        assertFalse(login.contains("contentDescription = \"SAFA Logo\""))
    }
}
