package com.safa.account.data.repository

import android.content.Context
import com.safa.account.data.api.ApiService
import com.safa.account.data.api.RetrofitClient
import com.safa.account.data.api.TokenManager
import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.local.LocalAccountBoundary
import com.safa.account.data.local.LocalFirstStore
import com.safa.account.data.model.*
import com.safa.account.data.money.MoneyMath
import com.safa.account.data.money.MoneyPayloadNormalizer
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.sync.SyncSnapshotGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical Android local-first repository.
 *
 * The repository is the persistence/merge boundary. Network interceptors may
 * reject obviously stale snapshots early, but correctness does not depend on
 * them: pending mutations, tombstones and sync revisions are checked again here
 * before any downloaded row can replace durable local state.
 */
class AppRepository private constructor(
    private val api: ApiService,
    private val localStore: LocalFirstStore?,
    private val appContext: Context?
) {
    constructor(api: ApiService) : this(api, null, null)
    constructor(context: Context) : this(
        remoteApi(context.applicationContext),
        LocalFirstStore(context.applicationContext),
        context.applicationContext
    )
    constructor(a: Context, b: Context, c: Context, d: Context, e: Context, f: Context, g: Context, h: Context, i: Context, j: Context) : this(a)

    private companion object {
        fun remoteApi(context: Context): ApiService {
            val tm = TokenManager(context.applicationContext)
            val base = tm.getBaseUrl().let { if (it.endsWith("/")) it else "$it/" }
            return RetrofitClient.getApiService(base, tm.getApiKey(), tm.getApiSecret(), tm)
        }
    }

    private val _operators = MutableStateFlow<List<OperatorAccount>>(emptyList())
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    private val _transactions = MutableStateFlow<List<RemittanceTransaction>>(emptyList())
    private val _deposits = MutableStateFlow<List<SupplierDeposit>>(emptyList())
    private val _expenses = MutableStateFlow<List<ExpenseIncome>>(emptyList())
    private val _rates = MutableStateFlow<List<DailyRate>>(emptyList())
    private val _ledgers = MutableStateFlow<List<WalletLedger>>(emptyList())
    private val _batches = MutableStateFlow<List<WalletBatch>>(emptyList())

    val allOperators: Flow<List<OperatorAccount>> = _operators.asStateFlow()
    val allCustomers: Flow<List<Customer>> = _customers.asStateFlow()
    val allCustomersRaw = allCustomers
    val allSuppliers: Flow<List<Supplier>> = _suppliers.asStateFlow()
    val allSuppliersRaw = allSuppliers
    val allTransactions: Flow<List<RemittanceTransaction>> = _transactions.asStateFlow()
    val allTransactionsRaw = allTransactions
    val allSupplierDeposits: Flow<List<SupplierDeposit>> = _deposits.asStateFlow()
    val allSupplierDepositsRaw = allSupplierDeposits
    val allExpensesIncomes: Flow<List<ExpenseIncome>> = _expenses.asStateFlow()
    val allExpensesIncomesRaw = allExpensesIncomes
    val allDailyRates: Flow<List<DailyRate>> = _rates.asStateFlow()
    val allWalletLedgers: Flow<List<WalletLedger>> = _ledgers.asStateFlow()
    val allWalletLedgersRaw = allWalletLedgers
    val allWalletBatches: Flow<List<WalletBatch>> = _batches.asStateFlow()
    val allWalletBatchesRaw = allWalletBatches

    init { loadLocalSnapshot() }

    private fun Any?.i(): Int = when (this) { is Number -> toInt(); else -> this?.toString()?.toIntOrNull() ?: 0 }
    private fun Any?.amount() = MoneyMath.amount(this)
    private fun Any?.nonNegativeAmount() = MoneyMath.nonNegativeAmount(this)
    private fun Any?.rate() = MoneyMath.rate(this)
    private fun Any?.nonNegativeRate() = MoneyMath.nonNegativeRate(this)
    private fun Any?.s(): String = this?.toString() ?: ""
    private fun Any?.b(): Boolean = this == true || this?.toString()?.lowercase() in setOf("1", "true", "yes", "on")
    private fun Any?.time(): Long? = SyncSnapshotGuard.parseTimestamp(this)
    private fun Map<String, Any?>.v(vararg keys: String): Any? = keys.firstNotNullOfOrNull { this[it] }
    private fun mapToJson(map: Map<String, Any?>): String {
        val raw = JSONObject(map).toString()
        return MoneyPayloadNormalizer.normalizeJson(raw) ?: raw
    }
    private fun jsonToMap(json: String): Map<String, Any?> = jsonObjectToMap(JSONObject(json))
    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> = buildMap { obj.keys().forEach { key -> put(key, jsonValue(obj.get(key))) } }
    private fun jsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> buildList { for (index in 0 until value.length()) add(jsonValue(value.get(index))) }
        else -> value
    }

    private fun customer(m: Map<String, Any?>) = Customer(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(),
        name = m.v("name").s(), phone = m.v("phone").s(), address = m.v("address").s(),
        securityNotes = m.v("security_notes").s(), avatarColor = m.v("avatar_color").s().ifBlank { "4280391411" },
        avatarEmoji = m.v("avatar_emoji").s().ifBlank { "👤" }, timestamp = m.v("timestamp", "created_at").time() ?: 0L,
        deletedAt = m.v("deleted_at").time()
    )

    private fun supplier(m: Map<String, Any?>) = Supplier(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(),
        name = m.v("name").s(), phone = m.v("phone").s(), address = m.v("address").s(),
        tradeLicense = m.v("trade_license").s(), securityNotes = m.v("security_notes").s(),
        avatarColor = m.v("avatar_color").s().ifBlank { "4280391411" }, avatarEmoji = m.v("avatar_emoji").s().ifBlank { "🏢" },
        timestamp = m.v("timestamp", "created_at").time() ?: 0L, deletedAt = m.v("deleted_at").time()
    )

    private fun transaction(m: Map<String, Any?>) = RemittanceTransaction(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(), customerId = m.v("customer_id").i(),
        supplierId = m.v("supplier_id").i(), amountSar = m.v("amount_sar", "amount").nonNegativeAmount(), customerRate = m.v("customer_rate").nonNegativeRate(),
        supplierRate = m.v("supplier_rate").nonNegativeRate(), amountBdt = m.v("amount_bdt").nonNegativeAmount(), sarCollected = m.v("sar_collected").let { if (it == null) m.v("amount_sar", "amount").nonNegativeAmount() else it.amount() },
        bdtDisbursed = m.v("bdt_disbursed").let { if (it == null) m.v("amount_bdt").nonNegativeAmount() else it.nonNegativeAmount() }, receiverName = m.v("receiver_name").s(),
        receiverPhone = m.v("receiver_phone").s(), receiverAccountType = m.v("receiver_account_type").s(), receiverAccountNo = m.v("receiver_account_no").s(),
        status = m.v("status", "type").s().ifBlank { "Pending" }, operatorId = m.v("operator_id").i(), walletBatchId = m.v("wallet_batch_id").i(),
        notes = m.v("notes").s(), timestamp = m.v("timestamp", "created_at").time() ?: 0L, deletedAt = m.v("deleted_at").time()
    )

    private fun deposit(m: Map<String, Any?>) = SupplierDeposit(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(), supplierId = m.v("supplier_id").i(),
        amountSar = m.v("amount_sar", "amount").nonNegativeAmount(), rate = m.v("rate", "supplier_rate").nonNegativeRate(), amountBdt = m.v("amount_bdt").nonNegativeAmount(),
        paidBdt = m.v("paid_bdt").nonNegativeAmount(), transactionType = m.v("transaction_type", "type").s().ifBlank { "SAR_GIVEN" }, notes = m.v("notes").s(),
        timestamp = m.v("timestamp", "created_at").time() ?: 0L, deletedAt = m.v("deleted_at").time()
    )

    private fun expense(m: Map<String, Any?>) = ExpenseIncome(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(), title = m.v("title", "name").s(),
        amount = m.v("amount").nonNegativeAmount(), currency = m.v("currency").s().ifBlank { "BDT" }, isExpense = m.v("is_expense").b(),
        category = m.v("category").s().ifBlank { "General" }, timestamp = m.v("timestamp", "created_at").time() ?: 0L,
        deletedAt = m.v("deleted_at").time()
    )

    private fun ledger(m: Map<String, Any?>) = WalletLedger(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(), name = m.v("name").s(),
        timestamp = m.v("timestamp", "created_at").time() ?: 0L, deletedAt = m.v("deleted_at").time()
    )

    private fun batch(m: Map<String, Any?>) = WalletBatch(
        id = m.v("local_id", "id").i(), serverId = m.v("server_id", "id").i(), ledgerId = m.v("ledger_id").i(), rate = m.v("rate").nonNegativeRate(),
        initialBdt = m.v("initial_bdt").nonNegativeAmount(), remainingBdt = m.v("remaining_bdt").nonNegativeAmount(), supplierId = m.v("supplier_id").i(),
        supplierDepositId = m.v("supplier_deposit_id").i(), notes = m.v("notes").s(), timestamp = m.v("timestamp", "created_at").time() ?: 0L,
        deletedAt = m.v("deleted_at").time()
    )

    private fun dailyRate(m: Map<String, Any?>) = DailyRate(
        date = m.v("date").s(),
        customerRate = m.v("customer_rate").nonNegativeRate(),
        supplierRate = m.v("supplier_rate").nonNegativeRate()
    )

    private fun cp(c: Customer) = mapOf("name" to c.name, "phone" to c.phone, "avatar_color" to c.avatarColor, "avatar_emoji" to c.avatarEmoji, "address" to c.address, "security_notes" to c.securityNotes, "local_id" to c.id, "server_id" to c.serverId, "timestamp" to c.timestamp, "deleted_at" to c.deletedAt)
    private fun sp(s: Supplier) = mapOf("name" to s.name, "phone" to s.phone, "avatar_color" to s.avatarColor, "avatar_emoji" to s.avatarEmoji, "address" to s.address, "trade_license" to s.tradeLicense, "security_notes" to s.securityNotes, "local_id" to s.id, "server_id" to s.serverId, "timestamp" to s.timestamp, "deleted_at" to s.deletedAt)
    private fun tp(t: RemittanceTransaction) = mapOf("customer_id" to t.customerId, "supplier_id" to t.supplierId, "amount_sar" to MoneyMath.nonNegativeAmount(t.amountSar).toPlainString(), "customer_rate" to MoneyMath.nonNegativeRate(t.customerRate).toPlainString(), "supplier_rate" to MoneyMath.nonNegativeRate(t.supplierRate).toPlainString(), "amount_bdt" to MoneyMath.nonNegativeAmount(t.amountBdt).toPlainString(), "sar_collected" to MoneyMath.amountString(t.sarCollected), "bdt_disbursed" to MoneyMath.nonNegativeAmount(t.bdtDisbursed).toPlainString(), "receiver_name" to t.receiverName, "receiver_phone" to t.receiverPhone, "receiver_account_type" to t.receiverAccountType, "receiver_account_no" to t.receiverAccountNo, "status" to t.status, "operator_id" to t.operatorId, "wallet_batch_id" to t.walletBatchId, "notes" to t.notes, "timestamp" to t.timestamp, "local_id" to t.id, "server_id" to t.serverId, "deleted_at" to t.deletedAt)
    private fun dp(d: SupplierDeposit) = mapOf("supplier_id" to d.supplierId, "amount_sar" to MoneyMath.nonNegativeAmount(d.amountSar).toPlainString(), "rate" to MoneyMath.nonNegativeRate(d.rate).toPlainString(), "amount_bdt" to MoneyMath.nonNegativeAmount(d.amountBdt).toPlainString(), "paid_bdt" to MoneyMath.nonNegativeAmount(d.paidBdt).toPlainString(), "transaction_type" to d.transactionType, "notes" to d.notes, "timestamp" to d.timestamp, "local_id" to d.id, "server_id" to d.serverId, "deleted_at" to d.deletedAt)
    private fun ep(e: ExpenseIncome) = mapOf("title" to e.title, "amount" to MoneyMath.nonNegativeAmount(e.amount).toPlainString(), "currency" to e.currency, "is_expense" to e.isExpense, "category" to e.category, "timestamp" to e.timestamp, "local_id" to e.id, "server_id" to e.serverId, "deleted_at" to e.deletedAt)
    private fun lp(l: WalletLedger) = mapOf("name" to l.name, "timestamp" to l.timestamp, "local_id" to l.id, "server_id" to l.serverId, "deleted_at" to l.deletedAt)
    private fun bp(b: WalletBatch) = mapOf("ledger_id" to b.ledgerId, "rate" to MoneyMath.nonNegativeRate(b.rate).toPlainString(), "initial_bdt" to MoneyMath.nonNegativeAmount(b.initialBdt).toPlainString(), "remaining_bdt" to MoneyMath.nonNegativeAmount(b.remainingBdt).toPlainString(), "supplier_id" to b.supplierId, "supplier_deposit_id" to b.supplierDepositId, "notes" to b.notes, "timestamp" to b.timestamp, "local_id" to b.id, "server_id" to b.serverId, "deleted_at" to b.deletedAt)
    private fun rp(r: DailyRate) = mapOf("date" to r.date, "customer_rate" to MoneyMath.rateString(r.customerRate), "supplier_rate" to MoneyMath.rateString(r.supplierRate))

    private fun persist(entity: String, localId: Int, serverId: Int, payload: Map<String, Any?>, operation: String, status: Int = LocalFirstStore.PENDING) {
        val store = localStore ?: return
        val json = mapToJson(payload)
        store.upsertRecord(entity, localId, serverId, json, status)
        if (status != LocalFirstStore.SYNCED) store.enqueue(entity, localId, serverId, operation, json)
    }

    private fun publish() {
        val store = localStore ?: return
        _customers.value = store.getRecordPayloads("customers").mapNotNull { runCatching { customer(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _suppliers.value = store.getRecordPayloads("suppliers").mapNotNull { runCatching { supplier(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _transactions.value = store.getRecordPayloads("transactions").mapNotNull { runCatching { transaction(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _deposits.value = store.getRecordPayloads("supplier_deposits").mapNotNull { runCatching { deposit(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _expenses.value = store.getRecordPayloads("expenses_incomes").mapNotNull { runCatching { expense(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _ledgers.value = store.getRecordPayloads("wallet_ledgers").mapNotNull { runCatching { ledger(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _batches.value = store.getRecordPayloads("wallet_batches").mapNotNull { runCatching { batch(jsonToMap(it.payload)) }.getOrNull() }.filter { it.deletedAt == null }
        _operators.value = store.getRecordPayloads("operators").mapNotNull { runCatching { operator(jsonToMap(it.payload)) }.getOrNull() }
        _rates.value = store.getRecordPayloads("daily_rates")
            .mapNotNull { runCatching { dailyRate(jsonToMap(it.payload)) }.getOrNull() }
            .filter { it.date.isNotBlank() }
            .sortedByDescending { it.date }
    }

    private fun loadLocalSnapshot() = runCatching { publish() }

    fun clearLocalPresentation() {
        _operators.value = emptyList(); _customers.value = emptyList(); _suppliers.value = emptyList(); _transactions.value = emptyList()
        _deposits.value = emptyList(); _expenses.value = emptyList(); _rates.value = emptyList(); _ledgers.value = emptyList(); _batches.value = emptyList()
    }

    fun boundAccountId(): Int? = appContext?.let(LocalAccountBoundary::currentAccountId)
    fun hasPendingLocalChanges(): Boolean = appContext?.let(LocalAccountBoundary::hasUnresolvedMutations) ?: false

    fun bindAccount(accountId: Int): LocalAccountBoundary.Result {
        val context = appContext ?: return LocalAccountBoundary.Result.UNCHANGED
        val result = LocalAccountBoundary.bind(context, accountId)
        if (result == LocalAccountBoundary.Result.SWITCHED) clearLocalPresentation()
        return result
    }

    private fun operator(m: Map<String, Any?>) = OperatorAccount(
        id = m.v("id", "server_id").i(), username = m.v("username", "name", "email").s(), role = m.v("role").s().ifBlank { "Staff" },
        mobile = m.v("mobile", "phone").s(), email = m.v("email").s(), isActivated = m.v("is_activated", "activated").b(), isActive = m.v("is_active", "active").b(),
        isBiometricEnabled = m.v("is_biometric_enabled").b(), canViewCustomers = m.v("can_view_customers").b(), canAddCustomers = m.v("can_add_customers").b(),
        canEditCustomers = m.v("can_edit_customers").b(), canDeleteCustomers = m.v("can_delete_customers").b(), canViewSuppliers = m.v("can_view_suppliers").b(),
        canAddSuppliers = m.v("can_add_suppliers").b(), canEditSuppliers = m.v("can_edit_suppliers").b(), canDeleteSuppliers = m.v("can_delete_suppliers").b(),
        canViewTransactions = m.v("can_view_transactions").b(), canAddTransactions = m.v("can_add_transactions").b(), canEditTransactions = m.v("can_edit_transactions").b(),
        canDeleteTransactions = m.v("can_delete_transactions").b(), canManageWallet = m.v("can_manage_wallet").b(), canManageExpenses = m.v("can_manage_expenses").b(), canViewReports = m.v("can_view_reports").b()
    )

    suspend fun refreshAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cursorStore = appContext?.let(::SyncCursorStore)
            var accountId = appContext?.let { TokenManager(it).getActiveAccountId() }?.takeIf { it > 0 }
            val initialState = accountId?.let { cursorStore?.read(it) }
            var cursor = initialState?.cursor ?: 0L
            var permissionScope = initialState?.permissionScope
            var pageCount = 0

            while (true) {
                check(++pageCount <= 10_000) { "Sync cursor page limit exceeded" }
                val response = api.syncDownPage(cursor)
                if (!response.isSuccessful || response.body() == null) error("Sync download failed: HTTP ${response.code()}")
                val body = response.body()!!
                if (body.protocol != null && body.protocol != "cursor-v1") error("Unsupported sync download protocol")
                if (body.protocol == "cursor-v1" && body.cursor != cursor) error("Server sync cursor mismatch")

                validateAndBindServerAccount(body.accountId)
                val responseAccountId = body.accountId?.takeIf { it > 0 }
                if (accountId != null && responseAccountId != null && accountId != responseAccountId) {
                    error("Server account context changed during synchronization")
                }
                accountId = responseAccountId ?: accountId

                val responsePermissionScope = body.permissionScope?.takeIf { it.isNotBlank() }
                if (body.protocol == "cursor-v1" && responsePermissionScope == null) {
                    error("Server sync permission scope is missing")
                }
                if (body.protocol == "cursor-v1" && cursor > 0L && permissionScope != responsePermissionScope) {
                    accountId?.let { id -> cursorStore?.resetForPermissionScope(id, responsePermissionScope!!) }
                    permissionScope = responsePermissionScope
                    cursor = 0L
                    continue
                }
                if (permissionScope == null) permissionScope = responsePermissionScope

                mergeServerRows("customers", body.customers, ::customer, ::cp)
                mergeServerRows("suppliers", body.suppliers, ::supplier, ::sp)
                mergeServerRows("transactions", body.transactions, ::transaction, ::tp)
                mergeServerRows("supplier_deposits", body.supplierDeposits, ::deposit, ::dp)
                mergeServerRows("expenses_incomes", body.expensesIncomes, ::expense, ::ep)
                mergeServerRows("wallet_ledgers", body.walletLedgers, ::ledger, ::lp)
                mergeServerRows("wallet_batches", body.walletBatches, ::batch, ::bp)

                val nextCursor = if (body.protocol == "cursor-v1") body.nextCursor else cursor
                if (nextCursor < cursor) error("Server sync cursor regressed")
                if (body.hasMore && nextCursor == cursor) error("Server sync cursor did not advance")

                if (body.protocol == "cursor-v1") {
                    accountId?.let { id -> cursorStore?.commit(id, nextCursor, permissionScope!!) }
                }
                cursor = nextCursor
                if (!body.hasMore) break
            }

            publish()
        }
    }

    private fun validateAndBindServerAccount(serverAccountId: Int?) {
        val context = appContext ?: return
        val accountId = serverAccountId?.takeIf { it > 0 } ?: return
        val tokenManager = TokenManager(context)
        val expected = tokenManager.getActiveAccountId()
        if (expected != null && expected != accountId) error("Server account context mismatch")
        val result = LocalAccountBoundary.bind(context, accountId)
        if (result == LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS) error("Account switch blocked by unresolved local mutations")
        if (result == LocalAccountBoundary.Result.SWITCHED) clearLocalPresentation()
        if (expected == null) tokenManager.saveActiveAccountId(accountId)
    }

    private data class LocalRecordState(val localId: Int, val syncStatus: Int, val syncVersion: Int)

    private fun findLocalRecordState(store: LocalFirstStore, entity: String, serverId: Int, requestedLocalId: Int?): LocalRecordState? {
        fun byServerId(): LocalRecordState? = store.readableDatabase.rawQuery(
            "SELECT local_id,sync_status,sync_version FROM records WHERE entity=? AND server_id=? LIMIT 1",
            arrayOf(entity, serverId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else LocalRecordState(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
        }

        fun byLocalId(localId: Int): LocalRecordState? = store.readableDatabase.rawQuery(
            "SELECT local_id,sync_status,sync_version FROM records WHERE entity=? AND local_id=? LIMIT 1",
            arrayOf(entity, localId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else LocalRecordState(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
        }

        return byServerId() ?: requestedLocalId?.let(::byLocalId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> mergeServerRows(entity: String, serverRows: List<Map<String, Any?>>, mapper: (Map<String, Any?>) -> T, payload: (T) -> Map<String, Any?>) {
        val store = localStore ?: return
        serverRows.forEach { raw ->
            val serverId = raw.v("id", "server_id").i()
            if (serverId <= 0) return@forEach
            val requestedLocalId = raw.v("local_id").i().takeIf { it > 0 }
            val existing = findLocalRecordState(store, entity, serverId, requestedLocalId)
            val localId = existing?.localId ?: requestedLocalId ?: store.nextLocalId()
            val incomingVersion = raw.v("sync_version", "version").i().coerceAtLeast(0)
            val localVersion = maxOf(store.serverVersion(entity, localId), existing?.syncVersion ?: 0)
            val hasLocalMutation = store.hasPending(entity, localId) || (existing != null && existing.syncStatus != LocalFirstStore.SYNCED)
            if (SyncSnapshotGuard.decide(incomingVersion.toLong(), localVersion.toLong(), hasLocalMutation) == SyncSnapshotGuard.Decision.IGNORE) return@forEach
            if (existing != null && incomingVersion == localVersion) return@forEach

            val item = mapper(raw)
            val model: T = when (item) {
                is Customer -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is Supplier -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is RemittanceTransaction -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is SupplierDeposit -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is ExpenseIncome -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is WalletLedger -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                is WalletBatch -> item.copy(id = localId, serverId = serverId, syncStatus = SyncStatus.SYNCED) as T
                else -> item
            }
            val durablePayload = payload(model).toMutableMap().apply { put("server_id", serverId); put("sync_version", incomingVersion) }
            store.upsertRecord(entity, localId, serverId, mapToJson(durablePayload), LocalFirstStore.SYNCED)
            store.recordServerVersion(entity, localId, serverId, incomingVersion)
        }
    }

    private fun localId(modelId: Int): Int = if (modelId > 0) modelId else localStore?.nextLocalId() ?: (System.currentTimeMillis() and 0x7fffffff).toInt()
    private fun isServerAcceptedCreate(serverId: Int, syncStatus: Int): Boolean = serverId > 0 && syncStatus == SyncStatus.SYNCED

    private enum class DeleteDisposition { LOCAL_CONFIRMED, REMOTE_ACCEPTED }

    private suspend fun confirmDelete(entity: String, serverId: Int): DeleteDisposition? {
        if (serverId > 0 && DeleteConfirmationCoordinator.consume("$entity:$serverId")) return DeleteDisposition.REMOTE_ACCEPTED
        return if (DeleteConfirmationCoordinator.requestConfirmation("Delete data?", "This action cannot be undone.")) {
            DeleteDisposition.LOCAL_CONFIRMED
        } else null
    }

    suspend fun insertCustomer(c: Customer): Int { val id = localId(c.id); val accepted = isServerAcceptedCreate(c.serverId, c.syncStatus); val x = if (accepted) c.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else c.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("customers", id, x.serverId, cp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateCustomer(c: Customer) { val x = c.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("customers", x.id, x.serverId, cp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteCustomerById(id: Int) { val current = getCustomerById(id) ?: return; val disposition = confirmDelete("customers", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("customers", x.id, x.serverId, cp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteCustomerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteCustomerById(id)
    suspend fun getCustomerById(id: Int) = _customers.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingCustomers() = pending("customers", ::customer)
    suspend fun markCustomerSynced(id: Int, serverId: Int) { localStore?.markSynced("customers", id, serverId); publish() }
    suspend fun markCustomerFailed(id: Int, error: String) { localStore?.markFailed("customers", id, error, false); publish() }
    suspend fun incrementCustomerRetry(id: Int) { localStore?.markFailed("customers", id, "retry", true); publish() }
    suspend fun resetCustomerRetry(id: Int, targetStatus: Int) { localStore?.retry("customers", id); publish() }
    suspend fun retryFailedCustomer(id: Int) { localStore?.retry("customers", id); publish() }

    suspend fun insertSupplier(s: Supplier): Int { val id = localId(s.id); val accepted = isServerAcceptedCreate(s.serverId, s.syncStatus); val x = if (accepted) s.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else s.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("suppliers", id, x.serverId, sp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateSupplier(s: Supplier) { val x = s.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("suppliers", x.id, x.serverId, sp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteSupplierById(id: Int) { val current = getSupplierById(id) ?: return; val disposition = confirmDelete("suppliers", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("suppliers", x.id, x.serverId, sp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteSupplierById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteSupplierById(id)
    suspend fun getSupplierById(id: Int) = _suppliers.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingSuppliers() = pending("suppliers", ::supplier)
    suspend fun markSupplierSynced(id: Int, serverId: Int) { localStore?.markSynced("suppliers", id, serverId); publish() }
    suspend fun markSupplierFailed(id: Int, error: String) { localStore?.markFailed("suppliers", id, error, false); publish() }
    suspend fun incrementSupplierRetry(id: Int) { localStore?.markFailed("suppliers", id, "retry", true); publish() }
    suspend fun resetSupplierRetry(id: Int, targetStatus: Int) { localStore?.retry("suppliers", id); publish() }
    suspend fun retryFailedSupplier(id: Int) { localStore?.retry("suppliers", id); publish() }

    suspend fun insertTransaction(t: RemittanceTransaction): Int { val id = localId(t.id); val accepted = isServerAcceptedCreate(t.serverId, t.syncStatus); val x = if (accepted) t.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else t.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("transactions", id, x.serverId, tp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateTransaction(t: RemittanceTransaction) { val x = t.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("transactions", x.id, x.serverId, tp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteTransactionById(id: Int) { val current = getTransactionById(id) ?: return; val disposition = confirmDelete("transactions", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("transactions", x.id, x.serverId, tp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteTransactionById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteTransactionById(id)
    suspend fun getTransactionById(id: Int) = _transactions.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingTransactions() = pending("transactions", ::transaction)
    suspend fun markTransactionSynced(id: Int, serverId: Int) { localStore?.markSynced("transactions", id, serverId); publish() }
    suspend fun markTransactionFailed(id: Int, error: String) { localStore?.markFailed("transactions", id, error, false); publish() }
    suspend fun incrementTransactionRetry(id: Int) { localStore?.markFailed("transactions", id, "retry", true); publish() }
    suspend fun resetTransactionRetry(id: Int, targetStatus: Int) { localStore?.retry("transactions", id); publish() }
    suspend fun retryFailedTransaction(id: Int) { localStore?.retry("transactions", id); publish() }

    suspend fun insertSupplierDeposit(d: SupplierDeposit): Int { val id = localId(d.id); val accepted = isServerAcceptedCreate(d.serverId, d.syncStatus); val x = if (accepted) d.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else d.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("supplier_deposits", id, x.serverId, dp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateSupplierDeposit(d: SupplierDeposit) { val x = d.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("supplier_deposits", x.id, x.serverId, dp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteSupplierDepositById(id: Int) { val current = getSupplierDepositById(id) ?: return; val disposition = confirmDelete("supplier_deposits", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("supplier_deposits", x.id, x.serverId, dp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteSupplierDepositById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteSupplierDepositById(id)
    suspend fun getSupplierDepositById(id: Int) = _deposits.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingSupplierDeposits() = pending("supplier_deposits", ::deposit)
    suspend fun markSupplierDepositSynced(id: Int, serverId: Int) { localStore?.markSynced("supplier_deposits", id, serverId); publish() }
    suspend fun markSupplierDepositFailed(id: Int, error: String) { localStore?.markFailed("supplier_deposits", id, error, false); publish() }
    suspend fun incrementSupplierDepositRetry(id: Int) { localStore?.markFailed("supplier_deposits", id, "retry", true); publish() }
    suspend fun resetSupplierDepositRetry(id: Int, targetStatus: Int) { localStore?.retry("supplier_deposits", id); publish() }
    suspend fun retryFailedSupplierDeposit(id: Int) { localStore?.retry("supplier_deposits", id); publish() }

    suspend fun insertExpenseIncome(e: ExpenseIncome): Int { val id = localId(e.id); val accepted = isServerAcceptedCreate(e.serverId, e.syncStatus); val x = if (accepted) e.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else e.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("expenses_incomes", id, x.serverId, ep(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateExpenseIncome(e: ExpenseIncome) { val x = e.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("expenses_incomes", x.id, x.serverId, ep(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteExpenseIncomeById(id: Int) { val current = getExpenseIncomeById(id) ?: return; val disposition = confirmDelete("expenses_incomes", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("expenses_incomes", x.id, x.serverId, ep(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteExpenseIncomeById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteExpenseIncomeById(id)
    suspend fun getExpenseIncomeById(id: Int) = _expenses.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingExpensesIncomes() = pending("expenses_incomes", ::expense)
    suspend fun markExpenseIncomeSynced(id: Int, serverId: Int) { localStore?.markSynced("expenses_incomes", id, serverId); publish() }
    suspend fun markExpenseIncomeFailed(id: Int, error: String) { localStore?.markFailed("expenses_incomes", id, error, false); publish() }
    suspend fun incrementExpenseIncomeRetry(id: Int) { localStore?.markFailed("expenses_incomes", id, "retry", true); publish() }
    suspend fun resetExpenseIncomeRetry(id: Int, targetStatus: Int) { localStore?.retry("expenses_incomes", id); publish() }
    suspend fun retryFailedExpenseIncome(id: Int) { localStore?.retry("expenses_incomes", id); publish() }

    suspend fun insertDailyRate(r: DailyRate) {
        val exact = r.copy(
            customerRate = MoneyMath.nonNegativeRate(r.customerRate),
            supplierRate = MoneyMath.nonNegativeRate(r.supplierRate)
        )
        val localId = exact.date.filter(Char::isDigit).toIntOrNull()
            ?.takeIf { it > 0 }
            ?: exact.date.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
        localStore?.upsertRecord("daily_rates", localId, 0, mapToJson(rp(exact)), LocalFirstStore.SYNCED)
        if (localStore != null) publish() else {
            _rates.value = (_rates.value.filterNot { it.date == exact.date } + exact).sortedByDescending { it.date }
        }
    }
    suspend fun getDailyRateByDate(date: String) = _rates.value.firstOrNull { it.date == date }

    suspend fun insertWalletLedger(l: WalletLedger): Int { val id = localId(l.id); val accepted = isServerAcceptedCreate(l.serverId, l.syncStatus); val x = if (accepted) l.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else l.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("wallet_ledgers", id, x.serverId, lp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateWalletLedger(l: WalletLedger) { val x = l.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("wallet_ledgers", x.id, x.serverId, lp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteWalletLedgerById(id: Int) { val current = getWalletLedgerById(id) ?: return; val disposition = confirmDelete("wallet_ledgers", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("wallet_ledgers", x.id, x.serverId, lp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteWalletLedgerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteWalletLedgerById(id)
    suspend fun getWalletLedgerById(id: Int) = _ledgers.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingWalletLedgers() = pending("wallet_ledgers", ::ledger)
    suspend fun markWalletLedgerSynced(id: Int, serverId: Int) { localStore?.markSynced("wallet_ledgers", id, serverId); publish() }
    suspend fun markWalletLedgerFailed(id: Int, error: String) { localStore?.markFailed("wallet_ledgers", id, error, false); publish() }
    suspend fun incrementWalletLedgerRetry(id: Int) { localStore?.markFailed("wallet_ledgers", id, "retry", true); publish() }
    suspend fun resetWalletLedgerRetry(id: Int, targetStatus: Int) { localStore?.retry("wallet_ledgers", id); publish() }
    suspend fun retryFailedWalletLedger(id: Int) { localStore?.retry("wallet_ledgers", id); publish() }

    suspend fun insertWalletBatch(b: WalletBatch): Int { val id = localId(b.id); val accepted = isServerAcceptedCreate(b.serverId, b.syncStatus); val x = if (accepted) b.copy(id = id, syncStatus = SyncStatus.SYNCED, syncError = null, retryCount = 0) else b.copy(id = id, serverId = 0, syncStatus = SyncStatus.PENDING_CREATE, syncError = null); persist("wallet_batches", id, x.serverId, bp(x), OutboxOperation.CREATE, if (accepted) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish(); return id }
    suspend fun updateWalletBatch(b: WalletBatch) { val x = b.copy(syncStatus = SyncStatus.PENDING_UPDATE, syncError = null, retryCount = 0, timestamp = System.currentTimeMillis()); persist("wallet_batches", x.id, x.serverId, bp(x), OutboxOperation.UPDATE); publish() }
    suspend fun deleteWalletBatchById(id: Int) { val current = getWalletBatchById(id) ?: return; val disposition = confirmDelete("wallet_batches", current.serverId) ?: return; val synced = disposition == DeleteDisposition.REMOTE_ACCEPTED; val x = current.copy(deletedAt = System.currentTimeMillis(), syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING_DELETE); persist("wallet_batches", x.id, x.serverId, bp(x), OutboxOperation.DELETE, if (synced) LocalFirstStore.SYNCED else LocalFirstStore.PENDING); publish() }
    suspend fun softDeleteWalletBatchById(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteWalletBatchById(id)
    suspend fun deleteWalletBatchBySupplierDepositId(id: Int) { _batches.value.firstOrNull { it.supplierDepositId == id }?.let { deleteWalletBatchById(it.id) } }
    suspend fun softDeleteWalletBatchBySupplierDepositId(id: Int, deletedAt: Long = System.currentTimeMillis()) = deleteWalletBatchBySupplierDepositId(id)
    suspend fun getWalletBatchById(id: Int) = _batches.value.firstOrNull { it.id == id || it.serverId == id }
    suspend fun getPendingWalletBatches() = pending("wallet_batches", ::batch)
    suspend fun markWalletBatchSynced(id: Int, serverId: Int) { localStore?.markSynced("wallet_batches", id, serverId); publish() }
    suspend fun markWalletBatchFailed(id: Int, error: String) { localStore?.markFailed("wallet_batches", id, error, false); publish() }
    suspend fun incrementWalletBatchRetry(id: Int) { localStore?.markFailed("wallet_batches", id, "retry", true); publish() }
    suspend fun resetWalletBatchRetry(id: Int, targetStatus: Int) { localStore?.retry("wallet_batches", id); publish() }
    suspend fun retryFailedWalletBatch(id: Int) { localStore?.retry("wallet_batches", id); publish() }

    private fun operatorMap(o: OperatorAccount) = mapOf("id" to o.id, "username" to o.username, "name" to o.username, "role" to o.role, "mobile" to o.mobile, "email" to o.email, "is_activated" to o.isActivated, "is_active" to o.isActive, "is_biometric_enabled" to o.isBiometricEnabled, "can_view_customers" to o.canViewCustomers, "can_add_customers" to o.canAddCustomers, "can_edit_customers" to o.canEditCustomers, "can_delete_customers" to o.canDeleteCustomers, "can_view_suppliers" to o.canViewSuppliers, "can_add_suppliers" to o.canAddSuppliers, "can_edit_suppliers" to o.canEditSuppliers, "can_delete_suppliers" to o.canDeleteSuppliers, "can_view_transactions" to o.canViewTransactions, "can_add_transactions" to o.canAddTransactions, "can_edit_transactions" to o.canEditTransactions, "can_delete_transactions" to o.canDeleteTransactions, "can_manage_wallet" to o.canManageWallet, "can_manage_expenses" to o.canManageExpenses, "can_view_reports" to o.canViewReports)
    suspend fun insertOperator(o: OperatorAccount): Int { val id = if (o.id > 0) o.id else localId(0); val x = o.copy(id = id); localStore?.upsertRecord("operators", id, id, mapToJson(operatorMap(x)), LocalFirstStore.SYNCED); publish(); return id }
    suspend fun updateOperator(o: OperatorAccount) { localStore?.upsertRecord("operators", o.id, o.id, mapToJson(operatorMap(o)), LocalFirstStore.SYNCED); publish() }
    suspend fun deleteOperator(o: OperatorAccount) {
        // User-management callers may invoke local cleanup even after a cancelled
        // or failed server DELETE. Treat the server list as authoritative and
        // remove the local row only after the operator is confirmed absent.
        val response = runCatching { api.getOperators() }.getOrNull() ?: return
        if (!response.isSuccessful || response.body() == null) return
        @Suppress("UNCHECKED_CAST")
        val body = response.body()!!
        val serverOperators = (body["users"] as? List<Map<String, Any?>>)
            ?: (body["operators"] as? List<Map<String, Any?>>)
            ?: return
        val stillExists = serverOperators.any { raw ->
            val id = (raw["id"] as? Number)?.toInt() ?: raw["id"]?.toString()?.toIntOrNull() ?: 0
            id == o.id
        }
        if (stillExists) return

        removeOperatorLocally(o)
    }

    suspend fun removeOperatorLocally(o: OperatorAccount) {
        val db = localStore?.writableDatabase ?: return
        db.beginTransaction()
        try {
            db.delete("records", "entity=? AND local_id=?", arrayOf("operators", o.id.toString()))
            db.delete("outbox", "entity=? AND local_id=?", arrayOf("operators", o.id.toString()))
            db.delete("server_versions", "entity=? AND local_id=?", arrayOf("operators", o.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publish()
    }
    suspend fun getOperatorByUsername(u: String) = _operators.value.firstOrNull { it.username == u }
    suspend fun getOperatorByMobile(m: String) = _operators.value.firstOrNull { it.mobile == m }

    private fun <T> pending(entity: String, mapper: (Map<String, Any?>) -> T): List<T> =
        localStore?.getRecordPayloads(entity)?.filter { it.syncStatus != LocalFirstStore.SYNCED && it.retryCount < 5 }
            ?.mapNotNull { runCatching { mapper(jsonToMap(it.payload)) }.getOrNull() } ?: emptyList()

    private fun canonicalEntity(raw: String): String = when (raw.trim().lowercase()) {
        "customer", "customers" -> "customers"
        "supplier", "suppliers" -> "suppliers"
        "transaction", "transactions" -> "transactions"
        "supplier_deposit", "supplier_deposits", "supplier-deposit", "supplier-deposits" -> "supplier_deposits"
        "expense_income", "expenses_incomes", "expense-income", "expenses-incomes" -> "expenses_incomes"
        "wallet_batch", "wallet_batches", "wallet-batch", "wallet-batches" -> "wallet_batches"
        "wallet_ledger", "wallet_ledgers", "wallet-ledger", "wallet-ledgers" -> "wallet_ledgers"
        "operator", "operators" -> "operators"
        else -> raw.trim().lowercase()
    }

    suspend fun enqueueOutbox(outbox: SyncOutbox) {
        val store = localStore ?: return
        val entity = canonicalEntity(outbox.entityType)
        val hasCanonicalPendingMutation = store.hasPending(entity, outbox.entityLocalId)

        // Modern repository mutations persist the record and canonical outbox in
        // one local-first operation. Legacy ViewModel enqueue calls must never
        // create a destructive mutation by themselves: if confirmation was
        // cancelled, there is no pending tombstone and this DELETE is ignored.
        if (hasCanonicalPendingMutation) return
        if (outbox.operation.equals(OutboxOperation.DELETE, ignoreCase = true)) return

        store.enqueue(entity, outbox.entityLocalId, outbox.entityServerId, outbox.operation, outbox.payloadJson)
    }
    suspend fun getPendingOutbox(): List<SyncOutbox> = localStore?.getReadyOutbox()?.map { SyncOutbox(id = it.id.toInt(), entityType = it.entity, entityLocalId = it.localId, entityServerId = it.serverId, operation = it.operation, payloadJson = it.payload, retryCount = it.retryCount, lastError = it.error) } ?: emptyList()
    suspend fun deleteOutbox(id: Int) { localStore?.deleteOutbox(id.toLong()) }

    private fun ensureMutationAccountBoundary() {
        val context = appContext ?: return
        val active = TokenManager(context).getActiveAccountId() ?: error("Active account selection is required before synchronization")
        val result = LocalAccountBoundary.bind(context, active)
        if (result == LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS) error("Local mutation account boundary mismatch")
    }

    suspend fun processOutbox(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val store = localStore ?: return@runCatching 0
            ensureMutationAccountBoundary()
            val pending = store.getReadyOutbox(50)
            if (pending.isEmpty()) return@runCatching 0
            store.markOutboxProcessing(pending.map { it.id })
            val grouped = pending.groupBy { it.entity }
            val response = api.syncUp(SyncUpPayload(
                transactions = grouped["transactions"].orEmpty().map { jsonToMap(it.payload) },
                customers = grouped["customers"].orEmpty().map { jsonToMap(it.payload) },
                suppliers = grouped["suppliers"].orEmpty().map { jsonToMap(it.payload) },
                supplierDeposits = grouped["supplier_deposits"].orEmpty().map { jsonToMap(it.payload) },
                expensesIncomes = grouped["expenses_incomes"].orEmpty().map { jsonToMap(it.payload) },
                walletBatches = grouped["wallet_batches"].orEmpty().map { jsonToMap(it.payload) },
                walletLedgers = grouped["wallet_ledgers"].orEmpty().map { jsonToMap(it.payload) }
            ))
            if (!response.isSuccessful || response.body() == null) {
                pending.forEach { store.markFailed(it.entity, it.localId, "HTTP ${response.code()}", response.code() >= 500 || response.code() == 408 || response.code() == 429) }
                error("Sync upload failed: HTTP ${response.code()}")
            }
            val body = response.body()!!
            val accepted = (body["accepted"] as? Map<*, *>) ?: emptyMap<String, Any?>()
            val rejected = (body["rejected"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            var count = 0
            pending.forEach { item ->
                val acceptedRows = accepted[item.entity] as? List<*>
                val row = acceptedRows?.mapNotNull { it as? Map<*, *> }?.firstOrNull { (it["local_id"] as? Number)?.toInt() == item.localId }
                val rejectedRow = rejected.firstOrNull { it["entity"]?.toString() == item.entity && (it["local_id"] as? Number)?.toInt() == item.localId }
                when {
                    row != null -> {
                        val serverId = (row["server_id"] as? Number)?.toInt() ?: item.serverId
                        val version = ((row["sync_version"] ?: row["version"]) as? Number)?.toInt() ?: store.serverVersion(item.entity, item.localId)
                        store.markSynced(item.entity, item.localId, serverId, version)
                        count++
                    }
                    rejectedRow != null -> store.markFailed(item.entity, item.localId, rejectedRow["reason"]?.toString() ?: "Server rejected record", false)
                    else -> store.markFailed(item.entity, item.localId, "Server did not acknowledge record", true)
                }
            }
            publish()
            count
        }
    }
}
