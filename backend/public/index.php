<?php

// Security Guard: Block direct access to hidden files, dotfiles, sensitive system files, or internal directories
$requestUri = rawurldecode($_SERVER['REQUEST_URI'] ?? '');
$parsedPath = parse_url($requestUri, PHP_URL_PATH) ?? '';

$blockedPatterns = [
    '/\.(env|git|gitignore|gitattributes|editorconfig|htaccess|npmrc|phpunit|lock|json|xml|yml|yaml|md|sh|bat|example)/i',
    '/(^|\/)\.(?!well-known)/i',
    '/(^|\/)(app|bootstrap|config|database|resources|routes|storage|tests|vendor)(\/|$)/i',
    '/(^|\/)(artisan|composer\.(json|lock)|package\.(json|lock)|phpunit\.xml|README\.md|vite\.config\.js)/i',
];

foreach ($blockedPatterns as $pattern) {
    if (preg_match($pattern, $parsedPath) || preg_match($pattern, $requestUri)) {
        http_response_code(404);
        header('X-Content-Type-Options: nosniff');
        header('X-Frame-Options: SAMEORIGIN');
        header('X-XSS-Protection: 1; mode=block');
        header('Content-Type: text/html; charset=utf-8');
        echo '<!DOCTYPE html><html><head><title>404 Not Found</title></head><body><h1>404 Not Found</h1></body></html>';
        exit;
    }
}

// Self-healing Favicon generator if missing or empty
$publicIco = __DIR__ . '/favicon.ico';
if (!file_exists($publicIco) || @filesize($publicIco) === 0) {
    $icoBase64 = 'AAABAAEAICAAAAAAAACoCAAAFgAAACgAAAAgAAAAQAAAAAEACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAgAAAAICAAIAAAACAAIAAgIAAAMDAwACAAIDAgICAgACAgIAAgACAgAMDAwADAwMAAgICAgACAgIAAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICACAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAgAAAAIAAAACAAAAAA';
    @file_put_contents($publicIco, base64_decode($icoBase64));
}

use Illuminate\Foundation\Application;
use Illuminate\Http\Request;

// PHP Version Safeguard (Laravel 11+ requires PHP >= 8.2)
if (version_compare(PHP_VERSION, '8.2.0', '<')) {
    http_response_code(500);
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: SAMEORIGIN');
    echo '<!DOCTYPE html><html><head><title>PHP Version Error</title><style>body{font-family:sans-serif;background:#f8fafc;padding:3rem;color:#1e293b;}.card{background:#fff;max-width:600px;margin:auto;padding:2rem;border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,0.1);border-left:6px solid #ef4444;}h2{color:#dc2626;margin-top:0;}</style></head><body>';
    echo '<div class="card">';
    echo '<h2>⚠️ PHP Version Mismatch / পিএইচপি ভার্সন ত্রুটি</h2>';
    echo '<p>Your cPanel server is currently running <strong>PHP ' . PHP_VERSION . '</strong>.</p>';
    echo '<p>Laravel requires <strong>PHP 8.2.0 or higher</strong> to function correctly.</p>';
    echo '<hr style="border:none;border-top:1px solid #e2e8f0;margin:1.5rem 0;">';
    echo '<p><strong>কি করতে হবে (Solution):</strong></p>';
    echo '<ol style="line-height:1.8;"><li>Log into your <strong>cPanel</strong>.</li><li>Go to <strong>MultiPHP Manager</strong> or <strong>Select PHP Version</strong>.</li><li>Change the PHP version for <code>safa.masarax.com</code> to <strong>PHP 8.2 or PHP 8.3</strong>.</li><li>Save changes and refresh this page.</li></ol>';
    echo '</div></body></html>';
    exit;
}

try {
    define('LARAVEL_START', microtime(true));

    // Ensure required storage directories exist and are writable
    $storageDirs = [
        __DIR__ . '/../storage/app',
        __DIR__ . '/../storage/framework/cache/data',
        __DIR__ . '/../storage/framework/sessions',
        __DIR__ . '/../storage/framework/views',
        __DIR__ . '/../storage/logs',
        __DIR__ . '/../bootstrap/cache',
    ];

    foreach ($storageDirs as $dir) {
        if (!is_dir($dir)) {
            @mkdir($dir, 0755, true);
        }
    }

    // Auto-initialize starter .env if missing before Laravel boots
    $envPath = __DIR__ . '/../.env';
    if (!file_exists($envPath)) {
        $randomKey = 'base64:' . base64_encode(random_bytes(32));
        $randomApiKey = 'safa_key_' . bin2hex(random_bytes(16));
        $randomApiSecret = 'safa_sec_' . bin2hex(random_bytes(32));
        $initialEnv = <<<EOT
APP_NAME=SAFA
APP_ENV=production
APP_KEY={$randomKey}
APP_DEBUG=false
APP_URL=https://safa.masarax.com

SAFA_API_KEY={$randomApiKey}
SAFA_API_SECRET={$randomApiSecret}

LOG_CHANNEL=stack
LOG_LEVEL=debug

DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=
DB_USERNAME=
DB_PASSWORD=

SESSION_DRIVER=file
CACHE_STORE=file
QUEUE_CONNECTION=sync
FILESYSTEM_DISK=local

APP_INSTALLED=false
EOT;
        @file_put_contents($envPath, $initialEnv);
    }

    if (file_exists($maintenance = __DIR__.'/../storage/framework/maintenance.php')) {
        require $maintenance;
    }

    require __DIR__.'/../vendor/autoload.php';

    /** @var Application $app */
    $app = require_once __DIR__.'/../bootstrap/app.php';

    $app->handleRequest(Request::capture());
} catch (\Throwable $e) {
    http_response_code(500);
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: SAMEORIGIN');

    $debugMode = false;
    $envPath = __DIR__ . '/../.env';
    if (file_exists($envPath)) {
        $envContent = file_get_contents($envPath);
        if (preg_match('/^APP_DEBUG\s*=\s*true/mi', $envContent)) {
            $debugMode = true;
        }
    }

    echo '<!DOCTYPE html><html><head><title>System Error</title><style>body{font-family:sans-serif;background:#f8fafc;padding:3rem;color:#1e293b;}.card{background:#fff;max-width:700px;margin:auto;padding:2rem;border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,0.1);border-left:6px solid #ef4444;}pre{background:#f1f5f9;padding:1rem;border-radius:8px;overflow-x:auto;}</style></head><body>';
    echo '<div class="card">';
    echo '<h2 style="color:#dc2626;margin-top:0;">⚠️ System Exception Detected / সিস্টেমে ত্রুটি পাওয়া গেছে</h2>';
    if ($debugMode) {
        echo '<p><strong>Message:</strong> ' . htmlspecialchars($e->getMessage()) . '</p>';
        echo '<p><strong>File:</strong> ' . htmlspecialchars($e->getFile()) . ' (Line ' . $e->getLine() . ')</p>';
        echo '<pre>' . htmlspecialchars($e->getTraceAsString()) . '</pre>';
    } else {
        echo '<p>An internal server error occurred. Please contact the system administrator.</p>';
    }
    echo '</div></body></html>';
    exit;
}
