<?php

use Illuminate\Support\Str;

$isProduction = env('APP_ENV', 'production') === 'production';

return [
    // File-backed browser sessions keep bootstrap independent of database schema
    // while HttpOnly/Secure/SameSite cookies protect the browser session ID.
    'driver' => env('SESSION_DRIVER', 'file'),
    'lifetime' => (int) env('SESSION_LIFETIME', 120),
    'expire_on_close' => env('SESSION_EXPIRE_ON_CLOSE', false),
    'encrypt' => env('SESSION_ENCRYPT', false),
    'files' => storage_path('framework/sessions'),
    'connection' => env('SESSION_CONNECTION'),
    'table' => env('SESSION_TABLE', 'sessions'),
    'store' => env('SESSION_STORE'),
    'lottery' => [2, 100],
    'cookie' => env('SESSION_COOKIE', Str::slug((string) env('APP_NAME', 'safa')).'-session'),
    'path' => env('SESSION_PATH', '/'),
    'domain' => env('SESSION_DOMAIN'),
    'secure' => env('SESSION_SECURE_COOKIE', $isProduction),
    'http_only' => env('SESSION_HTTP_ONLY', true),
    'same_site' => env('SESSION_SAME_SITE', 'strict'),
    'partitioned' => env('SESSION_PARTITIONED_COOKIE', false),
    'serialization' => 'json',
];
