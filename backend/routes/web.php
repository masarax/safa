<?php

use Illuminate\Support\Facades\Route;

// SAFA is API-only. Explicitly absorb every legacy browser/installer path so
// no method-specific route can leak a 405 or reach the retired installer
// controller. The production product has no web installer surface.
$closedInstallerResponse = fn () => response()->json(['status' => 'not_found'], 404);
Route::any('/install', $closedInstallerResponse);
Route::any('/install/test-db', $closedInstallerResponse);
Route::any('/install/process', $closedInstallerResponse);
Route::any('/install/update', $closedInstallerResponse);
Route::any('/install/update-process', $closedInstallerResponse);
Route::any('/update-db', $closedInstallerResponse);

// Branding assets are intentionally public, while the application remains
// API-only. Serve them explicitly so feature tests and non-static PHP servers
// receive the files instead of falling through to the 404 handler.
Route::get('/safa-logo.png', function () {
    return response()->file(public_path('safa-logo.png'));
});

Route::get('/favicon.svg', function () {
    return response()->file(public_path('favicon.svg'));
});

// Uploaded branding is stored outside Laravel's storage symlink contract.
// Route only generated raster logo names through the application so both a
// project-root and a public/ cPanel document root serve the same safe asset.
Route::get('/storage/logos/{file}', function (string $file) {
    $path = public_path('storage/logos/' . $file);
    if (!is_file($path)) return response()->json(['status' => 'not_found'], 404);

    return response()->file($path, [
        'Cache-Control' => 'public, max-age=86400',
        'X-Content-Type-Options' => 'nosniff',
    ]);
})->where('file', 'logo_[A-Za-z0-9_-]+\\.(?:png|jpe?g|gif|webp)');

// No browser-facing website or public status page is exposed.
Route::fallback(function () {
    return response()->json(['status' => 'not_found'], 404);
});
