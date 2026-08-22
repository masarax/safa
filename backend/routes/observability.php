<?php

use App\Http\Controllers\MobileTelemetryController;
use App\Http\Controllers\OpsMetricsController;
use App\Http\Controllers\OpsSyntheticController;
use App\Http\Middleware\CheckApiSecurityKey;
use Illuminate\Support\Facades\Route;

Route::get('/ops/metrics', OpsMetricsController::class)->middleware('throttle:60,1');
Route::post('/ops/synthetic-persistence', OpsSyntheticController::class)->middleware('throttle:12,1');
Route::post('/telemetry/mobile', MobileTelemetryController::class)
    ->middleware([CheckApiSecurityKey::class, 'throttle:120,1']);
