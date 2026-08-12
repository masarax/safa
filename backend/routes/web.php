<?php

use Illuminate\Support\Facades\Route;

// SAFA is API-only. No browser-facing website or status page is exposed.
Route::fallback(function () {
    return response()->json(['status' => 'not_found'], 404);
});
