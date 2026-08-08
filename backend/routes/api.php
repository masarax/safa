<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\GraphQLController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;

// Auth Routes (5-Token Security System & Device Binding & Granular RBAC)
Route::prefix('auth')->group(function () {
    Route::post('/login', [AuthJWTController::class, 'login']);
    Route::post('/refresh', [AuthJWTController::class, 'refreshToken']);
    Route::post('/bind-device', [AuthJWTController::class, 'bindDevice']);
    Route::post('/activate-superadmin', [AuthJWTController::class, 'activateSuperAdmin']);

    // Operator Management (Granular RBAC)
    Route::get('/operators', [AuthJWTController::class, 'getOperators']);
    Route::post('/operators', [AuthJWTController::class, 'createOperator']);
    Route::put('/operators/{id?}', [AuthJWTController::class, 'updateOperator']);
    Route::delete('/operators/{id?}', [AuthJWTController::class, 'deleteOperator']);
    Route::match(['get', 'post', 'put', 'patch', 'delete'], '/operators/{id?}', [AuthJWTController::class, 'operators']);

    // Multi-Account Sharing & Switching
    Route::post('/share-account', [AuthJWTController::class, 'shareAccount']);
    Route::get('/shared-accounts', [AuthJWTController::class, 'getSharedAccounts']);
    Route::post('/switch-account', [AuthJWTController::class, 'switchAccount']);
});



// GraphQL API Endpoint (Protected by Multi-Level 5-Token Verification Middleware)
Route::middleware(['verify.multilevel.token', AuditLogMiddleware::class])->group(function () {
    Route::post('/graphql', [GraphQLController::class, 'handle']);
});

// Sync & Config REST Endpoints
Route::middleware([CheckApiSecurityKey::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
    Route::post('/sync/up', [SyncController::class, 'syncUp']);
    Route::get('/sync/down', [SyncController::class, 'syncDown']);
    Route::get('/config/remote', [RemoteConfigController::class, 'getRemoteConfig']);
    Route::post('/config/update', [RemoteConfigController::class, 'updateConfig']);
    Route::post('/upload/logo', [RemoteConfigController::class, 'uploadLogo']);
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);
});
