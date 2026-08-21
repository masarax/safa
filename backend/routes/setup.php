<?php

use App\Http\Controllers\FirstRunSetupController;
use Illuminate\Support\Facades\Route;

// Public status exposes only whether the server needs setup and the safe setup
// path. It never exposes the private setup code, database credentials or claims.
Route::get('/api/setup/status', [FirstRunSetupController::class, 'status'])
    ->middleware('throttle:30,1')
    ->name('setup.status');

// State authorization intentionally happens inside the controller before its
// file-backed limiter. That guarantees an already-consumed first-run route is
// always a hard 404 rather than leaking its former existence as HTTP 429.
Route::get('/setup', [FirstRunSetupController::class, 'index'])
    ->name('setup.index');

Route::get('/setup/database', [FirstRunSetupController::class, 'showDatabase'])
    ->name('setup.database.show');
Route::post('/setup/database', [FirstRunSetupController::class, 'runDatabase'])
    ->name('setup.database.run');

Route::get('/setup/admin', [FirstRunSetupController::class, 'showAdmin'])
    ->name('setup.admin.show');
Route::post('/setup/admin', [FirstRunSetupController::class, 'createAdmin'])
    ->name('setup.admin.create');
