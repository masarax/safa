<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\SyncController;
use App\Http\Controllers\RemoteConfigController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;

Route::middleware([CheckApiSecurityKey::class, AuditLogMiddleware::class, 'throttle:60,1'])->group(function () {
    Route::post('/sync/up', [SyncController::class, 'syncUp']);
    Route::get('/sync/down', [SyncController::class, 'syncDown']);
    Route::get('/config/remote', [RemoteConfigController::class, 'getRemoteConfig']);
    Route::get('/version/check', [RemoteConfigController::class, 'checkVersion']);
});

