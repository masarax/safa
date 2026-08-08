<?php

use Illuminate\Foundation\Application;
use Illuminate\Http\Request;

// Enable error reporting during initial setup to debug any environment issues
ini_set('display_errors', '1');
ini_set('display_startup_errors', '1');
error_reporting(E_ALL);

define('LARAVEL_START', microtime(true));

// Ensure required storage directories exist and are writable
$storageDirs = [
    __DIR__ . '/storage/app',
    __DIR__ . '/storage/framework/cache/data',
    __DIR__ . '/storage/framework/sessions',
    __DIR__ . '/storage/framework/views',
    __DIR__ . '/storage/logs',
    __DIR__ . '/bootstrap/cache',
];

foreach ($storageDirs as $dir) {
    if (!is_dir($dir)) {
        @mkdir($dir, 0755, true);
    }
}

// Auto-initialize starter .env if missing before Laravel boots
$envPath = __DIR__ . '/.env';
if (!file_exists($envPath)) {
    $randomKey = 'base64:' . base64_encode(random_bytes(32));
    $initialEnv = <<<EOT
APP_NAME=SAFA
APP_ENV=production
APP_KEY={$randomKey}
APP_DEBUG=true
APP_URL=https://safa.masarax.com

SAFA_API_KEY=safa_test_api_key_2026
SAFA_API_SECRET=safa_test_secret_32byteslong_2026

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

// Determine if the application is in maintenance mode...
if (file_exists($maintenance = __DIR__.'/storage/framework/maintenance.php')) {
    require $maintenance;
}

// Register the Composer autoloader...
require __DIR__.'/vendor/autoload.php';

// Bootstrap Laravel and handle the request...
/** @var Application $app */
$app = require_once __DIR__.'/bootstrap/app.php';

$app->handleRequest(Request::capture());
