<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\GraphQLController;
use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\RemoteBusinessController;
use App\Http\Controllers\CustomerController;
use App\Http\Controllers\SupplierController;
use App\Http\Controllers\TransactionController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;
use App\Http\Middleware\VerifyRefreshRequest;
use App\Http\Middleware\VerifyActiveAuthSession;
use App\Http\Middleware\RejectInactiveLogin;

/*
 |--------------------------------------------------------------------------
 | Authentication / account-context routes
 |--------------------------------------------------------------------------
 | Health is the only intentionally unauthenticated endpoint. Login and
 | first-time activation still require the application HMAC credential and
 | are rate limited. Every endpoint that exposes account/user data requires
 | the complete multi-level session after authentication.
 */
Route::prefix('auth')->group(function () {
    Route::get('/health', function () {
        return response()->json([
            'status' => 'ok',
            'service' => 'SAFA API',
        ]);
    });

    Route::post('/login', [AuthJWTController::class, 'login'])
        ->middleware([CheckApiSecurityKey::class, RejectInactiveLogin::class, 'throttle:5,1']);

    Route::post('/refresh', [AuthJWTController::class, 'refreshToken'])
        ->middleware([CheckApiSecurityKey::class, VerifyRefreshRequest::class, 'throttle:20,1']);

    Route::post('/bind-device', [AuthJWTController::class, 'bindDevice'])
        ->middleware([CheckApiSecurityKey::class, 'throttle:10,1']);

    Route::post('/activate-superadmin', [AuthJWTController::class, 'activateSuperAdmin'])
        ->middleware([CheckApiSecurityKey::class, 'throttle:3,1']);

    // Operator management is authenticated and still performs its own
    // SuperAdmin authorization check inside AuthJWTController.
    Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
        Route::get('/operators', [AuthJWTController::class, 'getOperators']);
        Route::post('/operators', [AuthJWTController::class, 'createOperator']);
        Route::put('/operators/{id?}', [AuthJWTController::class, 'updateOperator']);
        Route::delete('/operators/{id?}', [AuthJWTController::class, 'deleteOperator']);
        Route::match(['get', 'post', 'put', 'patch', 'delete'], '/operators/{id?}', [AuthJWTController::class, 'operators']);

        // Legacy account endpoints retained for compatibility, but no longer public.
        Route::post('/share-account', [AuthJWTController::class, 'shareAccount']);
        Route::get('/shared-accounts', [AuthJWTController::class, 'getSharedAccounts']);
        Route::post('/switch-account', [AuthJWTController::class, 'switchAccount']);
    });
});

// GraphQL is protected by both application HMAC and the authenticated session.
Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
    Route::post('/graphql', [GraphQLController::class, 'handle']);
});

// All Android business traffic requires HMAC + the current authenticated
// session. API-key-only access is intentionally no longer accepted.
Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
    Route::get('/sync/down', [SyncController::class, 'syncDown']);
    Route::post('/sync/up', [SyncController::class, 'syncUp']);

    Route::get('/config/remote', [RemoteConfigController::class, 'getRemoteConfig']);
    Route::post('/config/update', [RemoteConfigController::class, 'updateConfig']);
    Route::post('/upload/logo', [RemoteConfigController::class, 'uploadLogo']);
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);

    Route::get('/accounts', [AccountContextController::class, 'index']);
    Route::post('/accounts/switch', [AccountContextController::class, 'switch']);
    Route::post('/accounts/share', [AccountContextController::class, 'share']);

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
