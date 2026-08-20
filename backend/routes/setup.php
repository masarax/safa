<?php

use App\Http\Controllers\FirstRunSetupController;
use Illuminate\Support\Facades\Route;

Route::middleware('throttle:10,1')->group(function (): void {
    Route::get('/setup/database', [FirstRunSetupController::class, 'showDatabase'])
        ->name('setup.database.show');
    Route::post('/setup/database', [FirstRunSetupController::class, 'runDatabase'])
        ->middleware('throttle:3,1')
        ->name('setup.database.run');

    Route::get('/setup/admin', [FirstRunSetupController::class, 'showAdmin'])
        ->name('setup.admin.show');
    Route::post('/setup/admin', [FirstRunSetupController::class, 'createAdmin'])
        ->middleware('throttle:5,1')
        ->name('setup.admin.create');
});
