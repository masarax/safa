<?php

use Illuminate\Support\Facades\Route;

// SAFA is API-only. Explicitly absorb legacy browser/installer paths so they
// cannot resolve to a method-specific route and return 405 responses.
Route::any('/install', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/install/update', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/install/update-process', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/update-db', fn () => response()->json(['status' => 'not_found'], 404));

// Branding assets are intentionally public, while the application remains
// API-only. Serve them explicitly so feature tests and non-static PHP servers
// receive the files instead of falling through to the 404 handler.
Route::get('/safa-logo.png', function () {
    return response()->file(public_path('safa-logo.png'));
});

Route::get('/favicon.svg', function () {
    return response()->file(public_path('favicon.svg'));
});

// No browser-facing website or public status page is exposed.
Route::fallback(function () {
    return response()->json(['status' => 'not_found'], 404);
});
