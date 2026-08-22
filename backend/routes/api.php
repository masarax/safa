<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\SyncPageController;
use App\Http\Controllers\SyncDeltaController;
use App\Http\Controllers\LegacySyncDownController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\SecureAuthController;
use App\Http\Controllers\MobileLoginController;
use App\Http\Controllers\UserManagementController;
use App\Http\Controllers\GraphQLController;
use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\RemoteBusinessController;
use App\Http\Controllers\CustomerController;
use App\Http\Controllers\SupplierController;
use App\Http\Controllers\TransactionController;
use App\Http\Controllers\VersionedApiProxyController;
use App\Http\Controllers\VersionedCollectionController;
use App\Http\Controllers\ServiceHealthController;
use App\Http\Controllers\BackupHealthController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;
use App\Http\Middleware\VerifyActiveAuthSession;
use App\Http\Middleware\RejectInactiveLogin;
use App\Http\Middleware\RejectAmbiguousLoginIdentity;
use App\Http\Middleware\RequireBusinessPermission;
use App\Http\Middleware\RequireGraphQLPermission;
use App\Http\Middleware\ResolveGraphQLAccountContext;
use App\Http\Middleware\RequireAdmin;
use App\Http\Middleware\ThrottleMobileLoginAttempts;
use App\Http\Middleware\ValidateLogoUpload;
use App\Http\Middleware\ValidateSyncDependencies;

// Versioned high-volume reads use the same security boundary and named API
// limiter as the legacy API. The named limiter is user/session/device aware;
// the public Android client key is never used as a global shared bucket.
Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, RequireBusinessPermission::class, 'throttle:api'])->group(function () {
    Route::get('/v1/sync/down', SyncPageController::class);
    Route::get('/v1/sync/changes', SyncDeltaController::class);
    foreach (['customers', 'suppliers', 'transactions', 'wallet-ledgers', 'supplier-deposits', 'wallet-batches', 'expenses-incomes'] as $resource) {
        Route::get('/v1/' . $resource, VersionedCollectionController::class)->defaults('resource', $resource);
    }
});

// Compatibility for already-installed Android builds whose Retrofit base URL
// resolves auth/login beneath /api/v1. Authentication must never depend on the
// generic nested-request proxy: dispatch it directly to the same canonical
// controller and failure-aware login throttle used by /api/auth/login below.
Route::post('/v1/auth/login', [MobileLoginController::class, 'login'])
    ->middleware([RejectInactiveLogin::class, RejectAmbiguousLoginIdentity::class, ThrottleMobileLoginAttempts::class, 'throttle:60,1']);

// All other v1 requests are transparently dispatched to the existing routes,
// preserving their established authentication and business middleware.
Route::any('/v1/{path?}', VersionedApiProxyController::class)->where('path', '.*');

Route::prefix('auth')->group(function () {
    Route::get('/health', ServiceHealthController::class)->middleware('throttle:30,1');
    Route::get('/backup-health', BackupHealthController::class)->middleware('throttle:12,1');
    Route::post('/login', [MobileLoginController::class, 'login'])->middleware([RejectInactiveLogin::class, RejectAmbiguousLoginIdentity::class, ThrottleMobileLoginAttempts::class, 'throttle:60,1']);
    Route::post('/refresh', [SecureAuthController::class, 'refresh'])->middleware([CheckApiSecurityKey::class, 'throttle:20,1']);
    Route::get('/session', [SecureAuthController::class, 'session'])->middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, 'throttle:api']);
    Route::post('/logout', [SecureAuthController::class, 'logout'])->middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, 'throttle:20,1']);
    Route::post('/logout-all', [SecureAuthController::class, 'logoutAll'])->middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, 'throttle:10,1']);
    Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, 'throttle:api'])->group(function () {
        Route::post('/change-pin', [SecureAuthController::class, 'changePin'])->middleware('throttle:5,1');
        Route::get('/operators', [UserManagementController::class, 'index']);
        Route::post('/operators', [UserManagementController::class, 'store']);
        Route::put('/operators/{id}', [UserManagementController::class, 'update']);
        Route::patch('/operators/{id}', [UserManagementController::class, 'update']);
        Route::delete('/operators/{id}', [UserManagementController::class, 'destroy']);
        Route::post('/share-account', [AccountContextController::class, 'share']);
        Route::get('/shared-accounts', [AuthJWTController::class, 'getSharedAccounts']);
        Route::post('/switch-account', [AccountContextController::class, 'switch']);
    });
});

Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, ResolveGraphQLAccountContext::class, RequireGraphQLPermission::class, AuditLogMiddleware::class, 'throttle:api'])->group(function () {
    Route::post('/graphql', [GraphQLController::class, 'handle']);
});

// Account discovery/switch/share establish or change tenant context. These
// routes cannot depend on the currently active business permission boundary:
// their controllers perform the exact owner/share authorization themselves.
Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, 'throttle:api'])->group(function () {
    Route::get('/accounts', [AccountContextController::class, 'index']);
    Route::post('/accounts/switch', [AccountContextController::class, 'switch']);
    Route::post('/accounts/share', [AccountContextController::class, 'share']);
});

Route::middleware([CheckApiSecurityKey::class, 'verify.multilevel.token', VerifyActiveAuthSession::class, AuditLogMiddleware::class, RequireBusinessPermission::class, 'throttle:api'])->group(function () {
    Route::get('/sync/down', LegacySyncDownController::class);
    Route::get('/sync/changes', SyncDeltaController::class);
    Route::post('/sync/up', [SyncController::class, 'syncUp'])->middleware(ValidateSyncDependencies::class);
    Route::get('/config/remote', [RemoteConfigController::class, 'getRemoteConfig']);
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);
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
    Route::post('/config/update', [RemoteConfigController::class, 'updateConfig'])->middleware(RequireAdmin::class);
    Route::post('/upload/logo', [RemoteConfigController::class, 'uploadLogo'])->middleware([RequireAdmin::class, ValidateLogoUpload::class]);
});
