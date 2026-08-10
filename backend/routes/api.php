<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\GraphQLController;
use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\RemoteBusinessController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;

// Auth Routes (5-Token Security System & Device Binding & Granular RBAC)
Route::prefix('auth')->group(function () {
    Route::post('/login', [AuthJWTController::class, 'login']);
    Route::post('/refresh', [AuthJWTController::class, 'refreshToken']);
    Route::post('/bind-device', [AuthJWTController::class, 'bindDevice']);
    Route::post('/activate-superadmin', [AuthJWTController::class, 'activateSuperAdmin']);

    Route::get('/operators', [AuthJWTController::class, 'getOperators']);
    Route::post('/operators', [AuthJWTController::class, 'createOperator']);
    Route::put('/operators/{id?}', [AuthJWTController::class, 'updateOperator']);
    Route::delete('/operators/{id?}', [AuthJWTController::class, 'deleteOperator']);
    Route::match(['get', 'post', 'put', 'patch', 'delete'], '/operators/{id?}', [AuthJWTController::class, 'operators']);

    // Legacy account endpoints retained for compatibility.
    Route::post('/share-account', [AuthJWTController::class, 'shareAccount']);
    Route::get('/shared-accounts', [AuthJWTController::class, 'getSharedAccounts']);
    Route::post('/switch-account', [AuthJWTController::class, 'switchAccount']);
});

Route::middleware(['verify.multilevel.token', AuditLogMiddleware::class])->group(function () {
    Route::post('/graphql', [GraphQLController::class, 'handle']);
});

use App\Http\Controllers\CustomerController;
use App\Http\Controllers\SupplierController;
use App\Http\Controllers\TransactionController;

// All Android business traffic uses the HMAC security middleware. Account context
// endpoints are exposed under the same /accounts paths used by ApiService.kt.
Route::middleware([CheckApiSecurityKey::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
    Route::get('/sync/down', [SyncController::class, 'syncDown']);
    Route::post('/sync/up', [SyncController::class, 'syncUp']);

    Route::get('/config/remote', [RemoteConfigController::class, 'getRemoteConfig']);
    Route::post('/config/update', [RemoteConfigController::class, 'updateConfig']);
    Route::post('/upload/logo', [RemoteConfigController::class, 'uploadLogo']);
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);

    // Server-authoritative account context used by Android.
    Route::get('/accounts', [AccountContextController::class, 'index']);
    Route::post('/accounts/switch', [AccountContextController::class, 'switch']);
    Route::post('/accounts/share', [AccountContextController::class, 'share']);

    // Direct REST CRUD endpoints.
    Route::get('/customers', [CustomerController::class, 'index']);
    Route::post('/customers', [CustomerController::class, 'store']);
    Route::put('/customers/{id}', [CustomerController::class, 'update']);
    Route::delete('/customers/{id}', [CustomerController::class, 'destroy']);

    Route::get('/suppliers', [SupplierController::class, 'index']);
    Route::post('/suppliers', [SupplierController::class, 'store']);
    Route::put('/suppliers/{id}', [SupplierController::class, 'update']);
    Route::delete('/suppliers/{id}', [SupplierController::class, 'destroy']);

    Route::get('/transactions', [TransactionController::class, 'index']);
    Route::post('/transactions', [TransactionController::class, 'store']);
    Route::put('/transactions/{id}', [TransactionController::class, 'update']);
    Route::delete('/transactions/{id}', [TransactionController::class, 'destroy']);

    // Server-authoritative endpoints for the remaining business resources.
    Route::get('/wallet-ledgers', [RemoteBusinessController::class, 'walletLedgers']);
    Route::post('/wallet-ledgers', [RemoteBusinessController::class, 'storeWalletLedger']);
    Route::put('/wallet-ledgers/{id}', [RemoteBusinessController::class, 'updateWalletLedger']);
    Route::delete('/wallet-ledgers/{id}', [RemoteBusinessController::class, 'destroyWalletLedger']);

    Route::get('/supplier-deposits', [RemoteBusinessController::class, 'supplierDeposits']);
    Route::post('/supplier-deposits', [RemoteBusinessController::class, 'storeSupplierDeposit']);
    Route::put('/supplier-deposits/{id}', [RemoteBusinessController::class, 'updateSupplierDeposit']);
    Route::delete('/supplier-deposits/{id}', [RemoteBusinessController::class, 'destroySupplierDeposit']);

    Route::get('/wallet-batches', [RemoteBusinessController::class, 'walletBatches']);
    Route::post('/wallet-batches', [RemoteBusinessController::class, 'storeWalletBatch']);
    Route::put('/wallet-batches/{id}', [RemoteBusinessController::class, 'updateWalletBatch']);
    Route::delete('/wallet-batches/{id}', [RemoteBusinessController::class, 'destroyWalletBatch']);

    Route::get('/expenses-incomes', [RemoteBusinessController::class, 'expensesIncomes']);
    Route::post('/expenses-incomes', [RemoteBusinessController::class, 'storeExpenseIncome']);
    Route::put('/expenses-incomes/{id}', [RemoteBusinessController::class, 'updateExpenseIncome']);
    Route::delete('/expenses-incomes/{id}', [RemoteBusinessController::class, 'destroyExpenseIncome']);
});