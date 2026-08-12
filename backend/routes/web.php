<?php

use Illuminate\Support\Facades\Route;

// SAFA is a private/API-first service. No public welcome, installer,
// migration runner, or database controls are exposed through the browser.
Route::get('/', function () {
    return response()->json([
        'status' => 'ok',
        'service' => 'SAFA',
        'message' => 'SAFA service is online.',
    ], 200);
})->name('home');

Route::get('/safa-logo.png', function () {
    $path = public_path('safa-logo.png');
    if (!is_file($path)) {
        return response()->json(['status' => 'error', 'message' => 'Logo not found'], 404);
    }
    return response()->file($path, ['Content-Type' => 'image/png']);
})->name('branding.logo');

Route::get('/favicon.ico', fn () => redirect()->route('branding.logo'))->name('branding.favicon.ico');
Route::get('/favicon.png', fn () => redirect()->route('branding.logo'))->name('branding.favicon.png');
Route::get('/favicon.svg', function () {
    $path = public_path('favicon.svg');
    if (!is_file($path)) return redirect()->route('branding.logo');
    return response()->file($path, ['Content-Type' => 'image/svg+xml']);
})->name('branding.favicon');
