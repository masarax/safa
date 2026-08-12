<?php

use Illuminate\Support\Facades\Route;

// SAFA is API-only. Explicitly absorb legacy browser/installer paths so they
// cannot resolve to a method-specific route and return 405 responses.
Route::any('/install', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/install/update', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/install/update-process', fn () => response()->json(['status' => 'not_found'], 404));
Route::any('/update-db', fn () => response()->json(['status' => 'not_found'], 404));

// No browser-facing website, status page, or asset endpoint is exposed.
Route::fallback(function () {
    return response()->json(['status' => 'not_found'], 404);
});
