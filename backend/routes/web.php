<?php

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Artisan;
use App\Http\Controllers\InstallerController;
use App\Http\Controllers\AccountContextController;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\CheckInstalled;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Http\Middleware\AuditLogMiddleware;

Route::get('/safa-logo.png', function () {
    try {
        $setting = \App\Models\SystemSetting::first();
        if ($setting && !empty($setting->app_logo_url)) {
            $pathInfo = parse_url($setting->app_logo_url, PHP_URL_PATH);
            if ($pathInfo) {
                $localStoragePath = public_path(ltrim($pathInfo, '/'));
                if (file_exists($localStoragePath) && is_file($localStoragePath)) {
                    $ext = pathinfo($localStoragePath, PATHINFO_EXTENSION);
                    $mime = match(strtolower($ext)) {
                        'png' => 'image/png',
                        'jpg', 'jpeg' => 'image/jpeg',
                        'svg' => 'image/svg+xml',
                        'webp' => 'image/webp',
                        'gif' => 'image/gif',
                        default => 'image/png'
                    };
                    return response()->file($localStoragePath, ['Content-Type' => $mime]);
                }
            }
        }
    } catch (\Throwable $e) {}

    $path = public_path('safa-logo.png');
    if (!file_exists($path)) return response()->json(['status' => 'error', 'message' => 'Logo asset not found'], 404);
    return response()->file($path, ['Content-Type' => 'image/png']);
})->name('branding.logo');

Route::get('/favicon.ico', function () {
    $path = public_path('safa-logo.png');
    if (!file_exists($path)) return response()->json(['status' => 'error', 'message' => 'Favicon asset not found'], 404);
    return response()->file($path, ['Content-Type' => 'image/png']);
})->name('branding.favicon.ico');

Route::get('/favicon.png', function () {
    $path = public_path('safa-logo.png');
    if (!file_exists($path)) return response()->json(['status' => 'error', 'message' => 'Favicon asset not found'], 404);
    return response()->file($path, ['Content-Type' => 'image/png']);
})->name('branding.favicon.png');

Route::get('/favicon.svg', function () {
    $path = public_path('favicon.svg');
    if (!file_exists($path)) return response()->json(['status' => 'error', 'message' => 'Favicon asset not found'], 404);
    return response()->file($path, ['Content-Type' => 'image/svg+xml']);
})->name('branding.favicon');

Route::middleware([EnsureNotInstalled::class])->group(function () {
    Route::get('/install', [InstallerController::class, 'index'])->name('install.index');
    Route::post('/install/process', [InstallerController::class, 'process'])->name('install.process');
    Route::post('/install/test-db', [InstallerController::class, 'testDb'])->name('install.test-db');
    Route::get('/install/success', [InstallerController::class, 'success'])->name('install.success');
});

Route::get('/install/update', [InstallerController::class, 'updateView'])->name('install.update-view');
Route::post('/install/update-process', [InstallerController::class, 'updateProcess'])->name('install.update-process');

// Account-context endpoints not present in the legacy API route file.
Route::middleware([CheckInstalled::class, CheckApiSecurityKey::class, AuditLogMiddleware::class])->prefix('api')->group(function () {
    Route::get('/accounts', [AccountContextController::class, 'index']);
    Route::post('/accounts/switch', [AccountContextController::class, 'switch']);
    Route::post('/accounts/share', [AccountContextController::class, 'share']);
});

Route::middleware([CheckInstalled::class])->group(function () {
    Route::get('/', function () {
        $pending = InstallerController::getPendingMigrations();
        if (!empty($pending)) {
            $updateToken = \Illuminate\Support\Str::random(64);
            session(['safa_update_token' => $updateToken]);
            return view('install_update', [
                'pendingMigrations' => $pending,
                'updateToken' => $updateToken
            ]);
        }
        return view('welcome');
    })->name('home');

    Route::post('/update-db', function (\Illuminate\Http\Request $request) {
        $secretKey = env('DB_UPDATE_SECRET');
        if (empty($secretKey)) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized. Database update secret is not configured on server (fail-closed).'], 403);
        }

        $providedKey = $request->input('key') ?: $request->header('X-SAFA-UPDATE-KEY');
        if (empty($providedKey) || !hash_equals((string) $secretKey, (string) $providedKey)) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized database update request. Valid security key required.'], 403);
        }

        try {
            $migrationFiles = glob(database_path('migrations/*.php')) ?: [];
            InstallerController::autoHealExistingSchema($migrationFiles);
            Artisan::call('migrate', ['--force' => true]);
            Artisan::call('config:clear');
            Artisan::call('cache:clear');
            Artisan::call('view:clear');
            return response()->json([
                'status' => 'success',
                'message' => 'Database schema updated successfully without any data loss.',
                'details' => trim(Artisan::output())
            ]);
        } catch (\Throwable $e) {
            report($e);
            return response()->json(['status' => 'error', 'message' => 'Database update failed.'], 500);
        }
    })->name('update.db');
});
