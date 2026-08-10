<?php

// Lightweight liveness endpoint for the Android pre-auth screen.
// Keep this independent of Laravel, database, route cache, sessions and HMAC.
// It is intentionally safe to expose publicly and returns no configuration data.

http_response_code(200);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');

echo json_encode([
    'status' => 'ok',
    'service' => 'SAFA API',
    'timestamp' => gmdate('c'),
], JSON_UNESCAPED_SLASHES);
