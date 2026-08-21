<?php

use App\Http\Controllers\FirstRunSetupController;
use App\Http\Controllers\OneTimeFrontendMigrationController;
use Illuminate\Support\Facades\Route;

// The very first web-facing maintenance action is independent of existing
// database contents. It is consumed only by its own durable completion state.
Route::get('/data-migration', [OneTimeFrontendMigrationController::class, 'show'])
    ->name('frontend.migration.show');
Route::post('/data-migration', [OneTimeFrontendMigrationController::class, 'run'])
    ->name('frontend.migration.run');

// Public status exposes only whether the server needs setup and the safe setup
// path. It never exposes the private setup code, database credentials or claims.
Route::get('/api/setup/status', [FirstRunSetupController::class, 'status'])
    ->middleware('throttle:30,1')
    ->name('setup.status');

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
