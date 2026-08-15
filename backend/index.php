<?php

// cPanel compatibility front controller for deployments whose document root
// points at the Laravel project root. Production configuration is immutable at
// request time: this file never creates .env files, credentials, directories or
// cache state. The public controller owns the safe JSON error boundary.

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
    '/(^|\/)(bootstrap|config|database|public|resources|routes|storage|tests|vendor)(\/|$)/i',
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

require __DIR__ . '/public/index.php';
