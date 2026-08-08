<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\GraphQLController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;

// Auth Routes (5-Token Security System & Device Binding)
Route::prefix('auth')->group(function () {
    Route::post('/login', [AuthJWTController::class, 'login']);
    Route::post('/refresh', [AuthJWTController::class, 'refreshToken']);
    Route::post('/bind-device', [AuthJWTController::class, 'bindDevice']);
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
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);
});
