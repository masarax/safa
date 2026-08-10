<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\InstallerController;
use App\Http\Controllers\DatabaseUpdateController;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\CheckInstalled;

Route::get('/safa-logo.png', function () {
    try {
        $setting = \App\Models\SystemSetting::first();
        if ($setting && !empty($setting->app_logo_url)) {
            $pathInfo = parse_url($setting->app_logo_url, PHP_URL_PATH);
            if ($pathInfo) {
                $localStoragePath = public_path(ltrim($pathInfo, '/'));
                if (file_exists($localStoragePath) && is_file($localStoragePath)) {
                    $ext = pathinfo($localStoragePath, PATHINFO_EXTENSION);
                    $mime = match (strtolower($ext)) {
                        'png' => 'image/png',
                        'jpg', 'jpeg' => 'image/jpeg',
                        'svg' => 'image/svg+xml',
                        'webp' => 'image/webp',
                        'gif' => 'image/gif',
                        default => 'image/png',
                    };
                    return response()->file($localStoragePath, ['Content-Type' => $mime]);
                }
            }
        }
    } catch (\Throwable $e) {
        report($e);
    }

    $path = public_path('safa-logo.png');
    if (!file_exists($path)) {
        return response()->json(['status' => 'error', 'message' => 'Logo asset not found'], 404);
    }

    return response()->file($path, ['Content-Type' => 'image/png']);
})->name('branding.logo');

Route::get('/favicon.ico', function () {
    return redirect()->route('branding.logo');
})->name('branding.favicon.ico');

Route::get('/favicon.png', function () {
    return redirect()->route('branding.logo');
})->name('branding.favicon.png');

Route::get('/favicon.svg', function () {
    $path = public_path('favicon.svg');
    if (!file_exists($path)) {
        return redirect()->route('branding.logo');
    }
    return response()->file($path, ['Content-Type' => 'image/svg+xml']);
})->name('branding.favicon');

Route::middleware([EnsureNotInstalled::class])->group(function () {
    Route::get('/install', [InstallerController::class, 'index'])->name('install.index');
    Route::post('/install/process', [InstallerController::class, 'process'])->name('install.process');
    Route::post('/install/test-db', [InstallerController::class, 'testDb'])->name('install.test-db');
    Route::get('/install/success', [InstallerController::class, 'success'])->name('install.success');
});

// Database update flow is intentionally independent from the DB-backed session table.
Route::get('/install/update', [DatabaseUpdateController::class, 'show'])->name('install.update-view');
Route::post('/install/update-process', [DatabaseUpdateController::class, 'process'])->name('install.update-process');

Route::middleware([CheckInstalled::class])->group(function () {
    Route::get('/', function () {
        $pending = DatabaseUpdateController::pendingMigrations();
        if (!empty($pending)) {
            return view('install_update', [
                'pendingMigrations' => $pending,
                'updateUrl' => \Illuminate\Support\Facades\URL::temporarySignedRoute(
                    'install.update-process',
                    now()->addMinutes(15)
                ),
            ]);
        }

        return view('welcome');
    })->name('home');
});
