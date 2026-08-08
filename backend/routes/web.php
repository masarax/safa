<?php

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Artisan;
use App\Http\Controllers\InstallerController;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\CheckInstalled;

// Installation & System Update Routes
Route::middleware([EnsureNotInstalled::class])->group(function () {
    Route::get('/install', [InstallerController::class, 'index'])->name('install.index');
    Route::post('/install/process', [InstallerController::class, 'process'])->name('install.process');
    Route::post('/install/test-db', [InstallerController::class, 'testDb'])->name('install.test-db');
    Route::get('/install/success', [InstallerController::class, 'success'])->name('install.success');
    Route::get('/install/update', [InstallerController::class, 'updateView'])->name('install.update-view');
    Route::post('/install/update-process', [InstallerController::class, 'updateProcess'])->name('install.update-process');
});

// Application Web Routes (protected by CheckInstalled middleware)
Route::middleware([CheckInstalled::class])->group(function () {
    Route::get('/', function () {
        // If pending database migrations/table updates exist on cPanel, show full install-style update page right on /
        $pending = InstallerController::getPendingMigrations();
        if (!empty($pending)) {
            return view('install_update', ['pendingMigrations' => $pending]);
        }

        // Once database is up to date, render the main index page
        return view('welcome');
    })->name('home');

    // API Database Update Endpoint
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
