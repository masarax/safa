<?php

use Illuminate\Foundation\Application;
use Illuminate\Http\Request;

// SAFA is a private API-only service. Every request that reaches this front
// controller must receive a machine-readable response; never render HTML.
$requestUri = rawurldecode($_SERVER['REQUEST_URI'] ?? '');
$parsedPath = parse_url($requestUri, PHP_URL_PATH) ?? '';

$blockedPatterns = [
    '/\.(env|git|gitignore|gitattributes|editorconfig|htaccess|npmrc|phpunit|lock|json|xml|yml|yaml|md|sh|bat|example)(?:$|[?#])/i',
    '/(^|\/)\.(?!well-known)/i',
    '/(^|\/)(app|bootstrap|config|database|resources|routes|storage|tests|vendor)(\/|$)/i',
    '/(^|\/)(artisan|composer\.(json|lock)|package\.(json|lock)|phpunit\.xml|README\.md|vite\.config\.js)(?:$|[?#])/i',
];

foreach ($blockedPatterns as $pattern) {
    if (preg_match($pattern, $parsedPath) || preg_match($pattern, $requestUri)) {
        http_response_code(404);
        header('Content-Type: application/json; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        echo json_encode(['status' => 'not_found'], JSON_UNESCAPED_SLASHES);
        exit;
    }
}

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');

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
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');

    // Never expose exception details from the public/private API entry point.
    echo json_encode(['status' => 'error', 'message' => 'Internal server error'], JSON_UNESCAPED_SLASHES);
    exit;
}
