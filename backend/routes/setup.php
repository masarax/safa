<?php

use App\Http\Controllers\ReleaseUpdateController;
use Illuminate\Support\Facades\Route;

// Legacy installer/setup routes are intentionally retired. These aliases keep
// existing internal route-name references compatible with the public /update
// release gate while CheckInstalled controls whether the route is visible.
Route::get('/update', [ReleaseUpdateController::class, 'show'])
    ->middleware('throttle:20,1')
    ->name('system.update.show');
Route::post('/update/run', [ReleaseUpdateController::class, 'run'])
    ->middleware('throttle:5,1')
    ->name('system.update.run');
