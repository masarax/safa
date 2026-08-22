package com.safa.account.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureArchitectureContractTest {
    private fun rootFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Repository file not found: $path")
    }

    @Test
    fun `feature repository ports do not depend on Android UI network or persistence infrastructure`() {
        val source = rootFile("app/src/main/java/com/safa/account/domain/feature/FeatureRepositories.kt").readText()
        listOf(
            "androidx.compose",
            "android.database",
            "com.safa.account.data.api",
            "com.safa.account.data.local",
            "com.safa.account.data.repository",
            "retrofit2",
        ).forEach { forbidden ->
            assertFalse("Feature ports must not depend on $forbidden", source.contains(forbidden))
        }

        listOf(
            "CustomerRepository",
            "SupplierRepository",
            "TransactionRepository",
            "SupplierFundingRepository",
            "WalletRepository",
            "ExpenseRepository",
            "RateRepository",
            "AdminRepository",
            "FeatureRepositorySet",
        ).forEach { contract ->
            assertTrue("Missing feature contract: $contract", source.contains("interface $contract"))
        }
    }

    @Test
    fun `feature adapters keep AppRepository as the single persistence and sync owner`() {
        val source = rootFile("app/src/main/java/com/safa/account/data/repository/FeatureRepositoryAdapters.kt").readText()
        assertTrue(source.contains("class AppFeatureRepositorySet(private val repository: AppRepository)"))
        assertFalse(source.contains("LocalFirstStore("))
        assertFalse(source.contains("SyncManager("))
        assertFalse(source.contains("RetrofitClient"))
    }

    @Test
    fun `customer use case stays infrastructure independent`() {
        val source = rootFile("app/src/main/java/com/safa/account/domain/feature/customer/CustomerUseCase.kt").readText()
        assertFeatureUseCaseIsInfrastructureIndependent("Customer", source)
        assertTrue(source.contains("class CustomerUseCase("))
        assertTrue(source.contains("private val repository: CustomerRepository"))
    }

    @Test
    fun `supplier use case stays infrastructure independent`() {
        val source = rootFile("app/src/main/java/com/safa/account/domain/feature/supplier/SupplierUseCase.kt").readText()
        assertFeatureUseCaseIsInfrastructureIndependent("Supplier", source)
        assertTrue(source.contains("class SupplierUseCase("))
        assertTrue(source.contains("private val repository: SupplierRepository"))
    }

    private fun assertFeatureUseCaseIsInfrastructureIndependent(feature: String, source: String) {
        listOf(
            "android.",
            "androidx.",
            "AppRepository",
            "SyncManager",
            "TokenManager",
            "RetrofitClient",
            "LocalFirstStore",
            "org.json",
        ).forEach { forbidden ->
            assertFalse("$feature use case must not depend on $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `SafaViewModel delegates customer mutations to customer feature boundary`() {
        val source = rootFile("app/src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()
        assertTrue(source.contains("private val customerUseCase: CustomerUseCase"))
        assertTrue(source.contains("featureRepositories.customers.items"))

        val updateBlock = source.substringAfter("fun updateCustomer(").substringBefore("fun updateSupplier(")
        assertTrue(updateBlock.contains("customerUseCase.update(customer)"))
        assertFalse(updateBlock.contains("repository.updateCustomer"))

        val customerBusiness = source.substringAfter("// 1. Save Customer").substringBefore("// 2. Save Supplier")
        assertTrue(customerBusiness.contains("customerUseCase.create("))
        assertTrue(customerBusiness.contains("customerUseCase.delete("))
        listOf(
            "repository.insertCustomer",
            "repository.getCustomerById",
            "repository.softDeleteCustomerById",
            "repository.deleteCustomerById",
            "repository.enqueueOutbox",
            "syncManager.getApiService().createCustomer",
            "syncManager.getApiService().deleteCustomerApi",
        ).forEach { forbidden ->
            assertFalse("Customer flow escaped the feature boundary through $forbidden", customerBusiness.contains(forbidden))
        }
    }

    @Test
    fun `SafaViewModel delegates supplier CRUD to supplier feature boundary`() {
        val source = rootFile("app/src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()
        assertTrue(source.contains("private val supplierUseCase: SupplierUseCase"))
        assertTrue(source.contains("featureRepositories.suppliers.items"))

        val updateBlock = source.substringAfter("fun updateSupplier(").substringBefore("// Lists representing reactive Flows")
        assertTrue(updateBlock.contains("supplierUseCase.update(supplier)"))
        assertFalse(updateBlock.contains("repository.updateSupplier"))

        val supplierBusiness = source.substringAfter("// 2. Save Supplier").substringBefore("// 3. Purchase / Deposit SAR")
        assertTrue(supplierBusiness.contains("supplierUseCase.create("))
        assertTrue(supplierBusiness.contains("supplierUseCase.delete("))
        listOf(
            "repository.insertSupplier",
            "repository.getSupplierById",
            "repository.softDeleteSupplierById",
            "repository.deleteSupplierById",
            "repository.enqueueOutbox",
            "syncManager.getApiService().createSupplier",
            "syncManager.getApiService().deleteSupplierApi",
        ).forEach { forbidden ->
            assertFalse("Supplier CRUD escaped the feature boundary through $forbidden", supplierBusiness.contains(forbidden))
        }

        val supplierFunding = source.substringAfter("// 3. Purchase / Deposit SAR").substringBefore("// --- Wallet Manual Operations ---")
        assertTrue("Supplier funding remains a separate follow-up slice", supplierFunding.contains("repository.insertSupplierDeposit"))
        assertTrue("Supplier funding-wallet coupling remains unchanged in this slice", supplierFunding.contains("repository.insertWalletBatch"))
    }

    @Test
    fun `business screens do not reach Retrofit SQLite or local store directly`() {
        val screens = listOf(
            "CustomerScreen.kt",
            "SupplierScreen.kt",
            "TransactionScreen.kt",
            "WalletScreen.kt",
            "ExpenseScreen.kt",
            "ReportsScreen.kt",
            "SettingsScreen.kt",
            "DashboardScreen.kt",
        )
        screens.forEach { name ->
            val source = rootFile("app/src/main/java/com/safa/account/ui/screens/$name").readText()
            listOf("RetrofitClient", "LocalFirstStore", "android.database", "androidx.room").forEach { forbidden ->
                assertFalse("$name must not reach $forbidden directly", source.contains(forbidden))
            }
        }
    }
}
