<?php

use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\CustomerController;
use App\Http\Controllers\DatabaseUpdateController;
use App\Http\Controllers\RemoteBusinessController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\SetupController;
use App\Http\Controllers\SupplierController;
use App\Http\Controllers\TransactionController;
use App\Http\Controllers\UserManagementController;
use App\Http\Controllers\WebAppController;
use App\Http\Controllers\WebAuthController;
use App\Http\Controllers\WebMobileFlowController;
use App\Http\Controllers\WebSettingsController;
use App\Http\Controllers\WebWorkspaceController;
use App\Http\Middleware\RequireAdmin;
use App\Http\Middleware\RequireBusinessPermission;
use App\Http\Middleware\ValidateLogoUpload;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

$closedInstallerResponse = fn () => response()->json(['status' => 'not_found'], 404);
Route::any('/install', $closedInstallerResponse);
Route::any('/install/test-db', $closedInstallerResponse);
Route::any('/install/process', $closedInstallerResponse);
Route::any('/install/update', $closedInstallerResponse);
Route::any('/install/update-process', $closedInstallerResponse);
Route::any('/update-db', $closedInstallerResponse);

Route::get('/safa-logo.png', fn () => response()->file(public_path('safa-logo.png')));
Route::get('/favicon.svg', fn () => response()->file(public_path('favicon.svg')));
Route::get('/safa-web.css', fn () => response()->file(public_path('safa-web.css'), ['Content-Type' => 'text/css; charset=utf-8', 'Cache-Control' => 'public, max-age=3600', 'X-Content-Type-Options' => 'nosniff']));
Route::get('/safa-web-product.css', fn () => response()->file(public_path('safa-web-product.css'), ['Content-Type' => 'text/css; charset=utf-8', 'Cache-Control' => 'public, max-age=3600', 'X-Content-Type-Options' => 'nosniff']));
Route::get('/safa-web.js', function () {
    $guard = (string) @file_get_contents(public_path('safa-web-events.js'));
    $runtime = (string) file_get_contents(public_path('safa-web.js'));
    return response($guard . "\n" . $runtime, 200, ['Content-Type' => 'application/javascript; charset=utf-8', 'Cache-Control' => 'public, max-age=3600', 'X-Content-Type-Options' => 'nosniff']);
});
Route::get('/safa-web-events.js', fn () => response()->file(public_path('safa-web-events.js'), ['Content-Type' => 'application/javascript; charset=utf-8', 'Cache-Control' => 'public, max-age=3600', 'X-Content-Type-Options' => 'nosniff']));
Route::get('/safa-web-product.js', fn () => response()->file(public_path('safa-web-product.js'), ['Content-Type' => 'application/javascript; charset=utf-8', 'Cache-Control' => 'public, max-age=3600', 'X-Content-Type-Options' => 'nosniff']));
Route::get('/storage/logos/{file}', function (string $file) {
    $path = public_path('storage/logos/' . $file);
    if (!is_file($path)) return response()->json(['status' => 'not_found'], 404);
    return response()->file($path, ['Cache-Control' => 'public, max-age=86400', 'X-Content-Type-Options' => 'nosniff']);
})->where('file', 'logo_[A-Za-z0-9_-]+\\.(?:png|jpe?g|gif|webp)');

Route::get('/index', [SetupController::class, 'show'])->name('safa.setup');
Route::post('/index/bootstrap', [SetupController::class, 'bootstrap'])->middleware('throttle:5,1')->name('safa.setup.bootstrap');
Route::post('/index/seed', [SetupController::class, 'seed'])->middleware(['auth', 'throttle:5,1'])->name('safa.setup.seed');

Route::get('/', fn (Request $request) => $request->user() ? redirect()->route('safa.app') : redirect()->route('safa.login'));
Route::middleware('guest')->group(function () {
    Route::get('/login', [WebAuthController::class, 'showLogin'])->name('safa.login');
    Route::post('/login', [WebAuthController::class, 'login'])->middleware('throttle:5,1')->name('safa.login.submit');
});
Route::post('/logout', [WebAuthController::class, 'logout'])->middleware('auth')->name('safa.logout');

Route::middleware(['auth', 'throttle:20,1'])->group(function () {
    Route::get('/system/update', [DatabaseUpdateController::class, 'show'])->name('system.update.show');
    Route::post('/system/update', [DatabaseUpdateController::class, 'process'])->name('system.update.process');
});

Route::middleware(['auth', 'throttle:120,1'])->group(function () {
    Route::get('/app', [WebAppController::class, 'index'])->name('safa.app');
    Route::get('/app/api/accounts', [AccountContextController::class, 'index'])->name('safa.web.accounts');
    Route::post('/app/api/accounts/switch', [AccountContextController::class, 'switch'])->name('safa.web.account.switch');

    Route::post('/app/api/settings/personal', [WebSettingsController::class, 'updatePersonal'])->name('safa.web.settings.personal');
    Route::post('/app/api/settings/profile', [WebSettingsController::class, 'updateProfile'])->name('safa.web.settings.profile');
    Route::post('/app/api/settings/pin', [WebSettingsController::class, 'changePin'])->name('safa.web.settings.pin');

    Route::get('/app/api/mobile/workspace', [WebWorkspaceController::class, 'index'])->name('safa.web.mobile.workspace');
    Route::post('/app/api/mobile/customer-sale', [WebMobileFlowController::class, 'customerSale'])->name('safa.web.mobile.customer-sale');
    Route::post('/app/api/mobile/customer-adjustment', [WebMobileFlowController::class, 'customerAdjustment'])->name('safa.web.mobile.customer-adjustment');
    Route::patch('/app/api/mobile/transactions/{id}', [WebMobileFlowController::class, 'updateTransaction'])->name('safa.web.mobile.transaction.update');
    Route::patch('/app/api/mobile/transactions/{id}/status', [WebMobileFlowController::class, 'transactionStatus'])->name('safa.web.mobile.transaction.status');
    Route::delete('/app/api/mobile/transactions/{id}', [WebMobileFlowController::class, 'deleteTransaction'])->name('safa.web.mobile.transaction.delete');
    Route::post('/app/api/mobile/supplier-funds', [WebMobileFlowController::class, 'supplierFund'])->name('safa.web.mobile.supplier-fund');
    Route::patch('/app/api/mobile/supplier-funds/{id}', [WebMobileFlowController::class, 'updateSupplierFund'])->name('safa.web.mobile.supplier-fund.update');
    Route::delete('/app/api/mobile/supplier-funds/{id}', [WebMobileFlowController::class, 'deleteSupplierFund'])->name('safa.web.mobile.supplier-fund.delete');
    Route::post('/app/api/mobile/wallet-ledgers', [WebMobileFlowController::class, 'createWalletLedger'])->name('safa.web.mobile.wallet-ledger.create');
    Route::patch('/app/api/mobile/wallet-ledgers/{id}', [WebMobileFlowController::class, 'renameWalletLedger'])->name('safa.web.mobile.wallet-ledger.rename');
    Route::delete('/app/api/mobile/wallet-ledgers/{id}', [WebMobileFlowController::class, 'deleteWalletLedger'])->name('safa.web.mobile.wallet-ledger.delete');
    Route::post('/app/api/mobile/wallet-deposit', [WebMobileFlowController::class, 'walletDeposit'])->name('safa.web.mobile.wallet-deposit');
    Route::post('/app/api/mobile/wallet-withdraw', [WebMobileFlowController::class, 'walletWithdraw'])->name('safa.web.mobile.wallet-withdraw');

    Route::middleware(RequireBusinessPermission::class)->group(function () {
        Route::get('/app/api/customers', [CustomerController::class, 'index'])->name('safa.web.customers');
        Route::post('/app/api/customers', [CustomerController::class, 'store']);
        Route::put('/app/api/customers/{id}', [CustomerController::class, 'update']);
        Route::delete('/app/api/customers/{id}', [CustomerController::class, 'destroy']);

        Route::get('/app/api/suppliers', [SupplierController::class, 'index'])->name('safa.web.suppliers');
        Route::post('/app/api/suppliers', [SupplierController::class, 'store']);
        Route::put('/app/api/suppliers/{id}', [SupplierController::class, 'update']);
        Route::delete('/app/api/suppliers/{id}', [SupplierController::class, 'destroy']);

        Route::get('/app/api/transactions', [TransactionController::class, 'index'])->name('safa.web.transactions');
        Route::post('/app/api/transactions', [TransactionController::class, 'store']);
        Route::put('/app/api/transactions/{id}', [TransactionController::class, 'update']);
        Route::delete('/app/api/transactions/{id}', [TransactionController::class, 'destroy']);

        Route::get('/app/api/wallet-ledgers', [RemoteBusinessController::class, 'walletLedgers'])->name('safa.web.wallet-ledgers');
        Route::post('/app/api/wallet-ledgers', [RemoteBusinessController::class, 'storeWalletLedger']);
        Route::put('/app/api/wallet-ledgers/{id}', [RemoteBusinessController::class, 'updateWalletLedger']);
        Route::delete('/app/api/wallet-ledgers/{id}', [RemoteBusinessController::class, 'destroyWalletLedger']);
        Route::get('/app/api/wallet-batches', [RemoteBusinessController::class, 'walletBatches'])->name('safa.web.wallet-batches');
        Route::post('/app/api/wallet-batches', [RemoteBusinessController::class, 'storeWalletBatch']);
        Route::put('/app/api/wallet-batches/{id}', [RemoteBusinessController::class, 'updateWalletBatch']);
        Route::delete('/app/api/wallet-batches/{id}', [RemoteBusinessController::class, 'destroyWalletBatch']);
        Route::get('/app/api/supplier-deposits', [RemoteBusinessController::class, 'supplierDeposits'])->name('safa.web.supplier-deposits');
        Route::post('/app/api/supplier-deposits', [RemoteBusinessController::class, 'storeSupplierDeposit']);
        Route::put('/app/api/supplier-deposits/{id}', [RemoteBusinessController::class, 'updateSupplierDeposit']);
        Route::delete('/app/api/supplier-deposits/{id}', [RemoteBusinessController::class, 'destroySupplierDeposit']);
        Route::get('/app/api/expenses', [RemoteBusinessController::class, 'expensesIncomes'])->name('safa.web.expenses');
        Route::post('/app/api/expenses', [RemoteBusinessController::class, 'storeExpenseIncome']);
        Route::put('/app/api/expenses/{id}', [RemoteBusinessController::class, 'updateExpenseIncome']);
        Route::delete('/app/api/expenses/{id}', [RemoteBusinessController::class, 'destroyExpenseIncome']);
    });

    Route::get('/app/api/users', [UserManagementController::class, 'index'])->name('safa.web.users');
    Route::post('/app/api/users', [UserManagementController::class, 'store']);
    Route::patch('/app/api/users/{id}', [UserManagementController::class, 'update']);
    Route::delete('/app/api/users/{id}', [UserManagementController::class, 'destroy']);
    Route::post('/app/api/config', [RemoteConfigController::class, 'updateConfig'])->middleware(RequireAdmin::class)->name('safa.web.config');
    Route::post('/app/api/logo', [RemoteConfigController::class, 'uploadLogo'])->middleware([RequireAdmin::class, ValidateLogoUpload::class])->name('safa.web.logo');
});

Route::fallback(function (Request $request) {
    if ($request->expectsJson()) return response()->json(['status' => 'not_found'], 404);
    abort(404);
});
