<?php

declare(strict_types=1);

use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\Artisan;

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('Cache-Control: no-store, max-age=0');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");

$projectRoot = is_file(__DIR__ . '/artisan') ? __DIR__ : dirname(__DIR__);
$lockPath = $projectRoot . '/storage/run-once.lock';
$runnerCopies = [
    $projectRoot . '/run-once.php',
    $projectRoot . '/public/run-once.php',
];

$notFound = static function (): never {
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Not Found';
    exit;
};

if (is_file($lockPath)) $notFound();
if (!in_array($_SERVER['REQUEST_METHOD'] ?? 'GET', ['GET', 'POST'], true)) {
    http_response_code(405);
    header('Allow: GET, POST');
    exit;
}

$render = static function (string $title, string $message, int $status = 200, bool $showForm = false): never {
    http_response_code($status);
    header('Content-Type: text/html; charset=utf-8');
    $safeTitle = htmlspecialchars($title, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $safeMessage = htmlspecialchars($message, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>' . $safeTitle . '</title>';
    echo '<style>body{font-family:system-ui,sans-serif;background:#f4f7f5;color:#17211d;margin:0;padding:32px}main{max-width:620px;margin:8vh auto;background:#fff;border:1px solid #dce5e0;border-radius:18px;padding:28px;box-shadow:0 16px 50px rgba(23,45,37,.09)}h1{margin-top:0}p{line-height:1.65;color:#52635b}label{display:grid;gap:8px;font-weight:700}input{padding:12px;border:1px solid #cddbd4;border-radius:10px}button{margin-top:14px;padding:11px 16px;border:0;border-radius:10px;background:#0b6b4f;color:#fff;font-weight:800;cursor:pointer}.note{font-size:.85rem;color:#6b7973}</style></head><body><main>';
    echo '<h1>' . $safeTitle . '</h1><p>' . $safeMessage . '</p>';
    if ($showForm) {
        echo '<form method="post" autocomplete="off"><label>One-time setup token<input type="password" name="token" required maxlength="512" autocomplete="off"></label><button type="submit">Run production setup once</button></form>';
        echo '<p class="note">This action runs production migrations, the safe system seeder, cache cleanup/rebuild, writes the installed marker, locks itself, and removes the runner files.</p>';
    }
    echo '</main></body></html>';
    exit;
};

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'GET') {
    $render('SAFA one-time production setup', 'Enter the deployment setup token to complete the pending production setup. This page permanently disables itself after one successful run.', 200, true);
}

$autoload = $projectRoot . '/vendor/autoload.php';
$bootstrap = $projectRoot . '/bootstrap/app.php';
if (!is_file($autoload) || !is_file($bootstrap)) {
    $render('Setup unavailable', 'The deployed Laravel runtime is incomplete.', 503);
}

try {
    require $autoload;
    $app = require $bootstrap;
    $app->make(Kernel::class)->bootstrap();

    $expectedToken = trim((string) env('SAFA_RUN_ONCE_TOKEN', ''));
    $providedToken = (string) ($_POST['token'] ?? '');
    if ($expectedToken === '' || !hash_equals($expectedToken, $providedToken)) {
        usleep(350000);
        $render('Setup denied', 'The one-time setup token is invalid or is not configured.', 403);
    }

    foreach ([
        $projectRoot . '/storage/framework/cache/data',
        $projectRoot . '/storage/framework/sessions',
        $projectRoot . '/storage/framework/views',
        $projectRoot . '/storage/logs',
        $projectRoot . '/public/storage/logos',
    ] as $directory) {
        if (!is_dir($directory) && !mkdir($directory, 0755, true) && !is_dir($directory)) {
            throw new RuntimeException('Required writable directory could not be created.');
        }
    }

    $commands = [
        ['migrate', ['--force' => true]],
        ['db:seed', ['--force' => true]],
        ['optimize:clear', []],
        ['config:cache', []],
        ['view:cache', []],
    ];

    foreach ($commands as [$command, $arguments]) {
        $exitCode = Artisan::call($command, $arguments);
        if ($exitCode !== 0) throw new RuntimeException('Production setup command failed.');
    }

    $installedMarker = $projectRoot . '/storage/installed';
    if (file_put_contents($installedMarker, gmdate(DATE_ATOM) . PHP_EOL, LOCK_EX) === false) {
        throw new RuntimeException('Installed marker could not be written.');
    }

    $build = null;
    $buildFile = $projectRoot . '/bootstrap/safa-build.json';
    if (is_file($buildFile)) {
        $decoded = json_decode((string) file_get_contents($buildFile), true);
        if (is_array($decoded) && isset($decoded['commit']) && preg_match('/^[a-f0-9]{40}$/', (string) $decoded['commit'])) {
            $build = (string) $decoded['commit'];
        }
    }

    $lockPayload = json_encode([
        'completed_at' => gmdate(DATE_ATOM),
        'build' => $build,
    ], JSON_UNESCAPED_SLASHES);
    if ($lockPayload === false || file_put_contents($lockPath, $lockPayload . PHP_EOL, LOCK_EX) === false) {
        throw new RuntimeException('Run-once lock could not be written.');
    }

    clearstatcache(true, $lockPath);
    foreach ($runnerCopies as $runner) {
        if (is_file($runner)) @unlink($runner);
    }

    clearstatcache();
    foreach ($runnerCopies as $runner) {
        if (is_file($runner)) {
            $render('Setup completed with cleanup required', 'Production setup completed and is locked, but a runner file could not be removed. Delete run-once.php from cPanel before continuing.', 500);
        }
    }

    $render('SAFA production setup completed', 'Database migrations, safe system seeding, cache preparation, and required runtime setup completed successfully. The one-time runner has deleted itself.', 200);
} catch (Throwable $exception) {
    $supportId = strtoupper(substr(hash('sha256', random_bytes(32)), 0, 10));
    error_log('SAFA_RUN_ONCE_FAILURE support_id=' . $supportId . ' type=' . get_class($exception));
    $render('Production setup failed', 'No sensitive diagnostic details are shown. Support ID: ' . $supportId, 500);
}
