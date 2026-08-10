<?php

use Illuminate\Support\Facades\Route;

// SAFA is a private/API-first service. The browser root intentionally exposes
// no welcome page, installer, migration runner, or database controls.
Route::get('/', function () {
    return response()->json([
        'status' => 'not_found',
        'message' => 'SAFA is a private application service.',
    ], 404);
})->name('home');

// Branding assets are the only public web resources intentionally exposed.
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
    if (!is_file($path)) {
        return redirect()->route('branding.logo');
    }
    return response()->file($path, ['Content-Type' => 'image/svg+xml']);
})->name('branding.favicon');
