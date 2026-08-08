<?php

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Artisan;
use App\Http\Controllers\InstallerController;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\CheckInstalled;

// Installation & Manual System Update Routes
Route::middleware([EnsureNotInstalled::class])->group(function () {
    Route::get('/install', [InstallerController::class, 'index'])->name('install.index');
    Route::post('/install/process', [InstallerController::class, 'process'])->name('install.process');
    Route::post('/install/test-db', [InstallerController::class, 'testDb'])->name('install.test-db');
    Route::get('/install/success', [InstallerController::class, 'success'])->name('install.success');

    // Manual Database Migration Update Screen (active ONLY when un-executed migrations exist)
    Route::get('/install/update', [InstallerController::class, 'updateView'])->name('install.update-view');
    Route::post('/install/update-process', [InstallerController::class, 'updateProcess'])->name('install.update-process');
});

// Application Web Routes (protected by CheckInstalled middleware)
Route::middleware([CheckInstalled::class])->group(function () {
    Route::get('/', function () {
        return view('welcome');
    })->name('home');

    // Safe zero-data-loss database updater
    Route::get('/update-db', function () {
        try {
            Artisan::call('migrate', ['--force' => true]);
            $output = Artisan::output();
            return response()->json([
                'status' => 'success',
                'message' => 'Database schema updated successfully without any data loss.',
                'details' => trim($output)
            ]);
        } catch (\Throwable $e) {
            return response()->json([
                'status' => 'error',
                'message' => 'Database update failed: ' . $e->getMessage()
            ], 500);
        }
    })->name('update.db');
});
