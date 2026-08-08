<?php

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Artisan;
use App\Http\Controllers\InstallerController;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\CheckInstalled;

// Installation Routes (only accessible when not installed)
Route::middleware([EnsureNotInstalled::class])->group(function () {
    Route::get('/install', [InstallerController::class, 'index'])->name('install.index');
    Route::post('/install/process', [InstallerController::class, 'process'])->name('install.process');
    Route::post('/install/test-db', [InstallerController::class, 'testDb'])->name('install.test-db');
    Route::get('/install/success', [InstallerController::class, 'success'])->name('install.success');
});

// Database Migration & System Update Routes (accessible when updates are pending)
Route::get('/install/update', [InstallerController::class, 'updateView'])->name('install.update-view');
Route::post('/install/update-process', [InstallerController::class, 'updateProcess'])->name('install.update-process');

// Application Web Routes (protected by CheckInstalled middleware)
Route::middleware([CheckInstalled::class])->group(function () {
    Route::get('/', function () {
        $pending = InstallerController::getPendingMigrations();
        if (!empty($pending)) {
            return view('install_update', ['pendingMigrations' => $pending]);
        }

        return view('welcome');
    })->name('home');

    // Convenient API Database Update Endpoint
    Route::get('/update-db', function () {
        try {
            Artisan::call('migrate', ['--force' => true]);
            Artisan::call('config:clear');
            Artisan::call('cache:clear');
            Artisan::call('view:clear');
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
