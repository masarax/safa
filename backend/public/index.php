<?php

use Illuminate\Foundation\Application;
use Illuminate\Http\Request;

// SAFA serves both the authenticated browser application and the JSON API.
// Sensitive repository/runtime paths are rejected before Laravel boots.
$requestUri = rawurldecode($_SERVER['REQUEST_URI'] ?? '');
$parsedPath = parse_url($requestUri, PHP_URL_PATH) ?? '';

// `/app` and `/app/api/*` are authenticated Laravel routes. Every other path
// containing an `app` segment is treated as attempted source-directory access.
$isBrowserAppRoute = preg_match('#^/app(?:/?|/api(?:/.*)?)$#i', $parsedPath) === 1;
$isBlockedRequest = !$isBrowserAppRoute
    && preg_match('/(^|\/)app(\/|$)/i', $parsedPath) === 1;

$blockedPatterns = [
    '/\.(env|git|gitignore|gitattributes|editorconfig|htaccess|npmrc|phpunit|lock|json|xml|yml|yaml|md|sh|bat|example)(?:$|[?#])/i',
    '/(^|\/)\.(?!well-known)/i',
    '/(^|\/)(bootstrap|config|database|resources|routes|storage|tests|vendor)(\/|$)/i',
    '/(^|\/)(artisan|composer\.(json|lock)|package\.(json|lock)|phpunit\.xml|README\.md|vite\.config\.js)(?:$|[?#])/i',
];

foreach ($blockedPatterns as $pattern) {
    if (preg_match($pattern, $parsedPath) || preg_match($pattern, $requestUri)) {
        $isBlockedRequest = true;
        break;
    }
}

if ($isBlockedRequest) {
    http_response_code(404);
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    echo json_encode(['status' => 'not_found'], JSON_UNESCAPED_SLASHES);
    exit;
}

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');

if (version_compare(PHP_VERSION, '8.2.0', '<')) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['status' => 'error', 'message' => 'Server configuration error'], JSON_UNESCAPED_SLASHES);
    exit;
}

try {
    define('LARAVEL_START', microtime(true));

    if (file_exists($maintenance = __DIR__.'/../storage/framework/maintenance.php')) {
        require $maintenance;
    }

    require __DIR__.'/../vendor/autoload.php';

    /** @var Application $app */
    $app = require_once __DIR__.'/../bootstrap/app.php';

    $app->handleRequest(Request::capture());
} catch (Throwable $e) {
    $supportId = strtoupper(substr(hash('sha256', random_bytes(32)), 0, 10));
    error_log('SAFA_FRONT_CONTROLLER_FAILURE support_id=' . $supportId . ' type=' . get_class($e));
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['status' => 'error', 'message' => 'Internal server error', 'support_id' => $supportId], JSON_UNESCAPED_SLASHES);
    exit;
}
