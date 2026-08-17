<?php

namespace Tests\Feature;

use Tests\TestCase;

class DeploymentContractTest extends TestCase
{
    public function test_cpanel_workflow_only_packages_dependencies_and_syncs_over_ftp(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));

        $this->assertStringContainsString("workflow_dispatch:\n", $workflow);
        $this->assertStringContainsString('composer install --no-dev --optimize-autoloader --no-interaction --prefer-dist --no-progress', $workflow);
        $this->assertStringContainsString('SamKirkland/FTP-Deploy-Action@v4.3.5', $workflow);
        $this->assertStringContainsString('local-dir: backend/', $workflow);
        $this->assertStringContainsString('server-dir: /', $workflow);

        preg_match_all('/secrets\.([A-Z0-9_]+)/', $workflow, $matches);
        $secrets = array_values(array_unique($matches[1] ?? []));
        sort($secrets);
        $this->assertSame(['FTP_PASSWORD', 'FTP_SERVER', 'FTP_USERNAME'], $secrets);

        foreach ([
            '.env',
            'public/storage/logos/**',
            'storage/installed',
            'storage/framework/cache/*',
            'storage/framework/sessions/*',
            'storage/framework/views/*',
            'storage/logs/*',
            'tests/',
        ] as $protectedPath) {
            $this->assertStringContainsString($protectedPath, $workflow);
        }

        foreach ([
            'Prepare test database',
            'Run mandatory full test suite',
            'artisan test',
            'PHP syntax check',
            'SAFA_MIGRATION_',
            'X-SAFA-Deploy-Token',
            'run-once.php',
            "Artisan::call('migrate'",
            'migrate --force',
            'optimize:clear',
            'config:cache',
            'view:cache',
            'curl --',
            'api/auth/health',
            'SAFA_DEPLOY_SHA',
            'openssl rand',
        ] as $forbiddenDeployConcern) {
            $this->assertStringNotContainsString($forbiddenDeployConcern, $workflow);
        }
    }

    public function test_database_maintenance_is_owned_by_authenticated_superadmin_application_flow(): void
    {
        $controller = (string) file_get_contents(app_path('Http/Controllers/DatabaseUpdateController.php'));
        $service = (string) file_get_contents(app_path('Services/DatabaseUpdateService.php'));
        $middleware = (string) file_get_contents(app_path('Http/Middleware/CheckInstalled.php'));
        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $publicHtaccess = (string) file_get_contents(public_path('.htaccess'));

        $this->assertStringContainsString("Route::get('/system/update'", $routes);
        $this->assertStringContainsString("Route::post('/system/update/run'", $routes);
        $this->assertStringContainsString("->name('system.update.show')", $routes);
        $this->assertStringContainsString("->name('system.update.run')", $routes);
        $this->assertStringNotContainsString("Route::post('/system/update/migrate'", $routes);
        $this->assertStringNotContainsString("Route::post('/system/update/seed'", $routes);

        $this->assertStringContainsString('$user->isSuperAdmin()', $controller);
        $this->assertStringNotContainsString("config('safa.maintenance_token'", $controller);
        $this->assertStringContainsString("Artisan::call('migrate', ['--force' => true])", $service);
        $this->assertStringContainsString('ReleaseDataUpdateSeeder::class', $service);
        $this->assertStringContainsString("Artisan::call('optimize:clear')", $service);
        $this->assertStringContainsString('flock($handle, LOCK_EX | LOCK_NB)', $service);
        $this->assertStringContainsString("redirect()->route('system.update.show')", $middleware);
        $this->assertStringContainsString("'status' => 'update_required'", $middleware);

        foreach (['migrate:fresh', 'migrate:reset', 'migrate:refresh', 'migrate:rollback', 'db:wipe', 'truncate'] as $destructiveCommand) {
            $this->assertStringNotContainsString($destructiveCommand, $service);
        }

        $this->assertFileDoesNotExist(base_path('deploy/run-once.php'));
        $this->assertStringNotContainsString('run-once.php', $publicHtaccess);
        $this->assertStringNotContainsString('safa-deploy-migrate-', $publicHtaccess);
    }

    public function test_signed_apk_workflow_is_secret_backed_and_publishes_checksum(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/release-apk.yml'));

        foreach ([
            'ANDROID_KEYSTORE_BASE64',
            'ANDROID_STORE_PASSWORD',
            'ANDROID_KEY_ALIAS',
            'ANDROID_KEY_PASSWORD',
        ] as $secret) {
            $this->assertStringContainsString($secret, $workflow);
        }

        $this->assertStringContainsString('testDebugUnitTest lintDebug', $workflow);
        $this->assertStringContainsString('assembleRelease', $workflow);
        $this->assertStringContainsString('apksigner verify', $workflow);
        $this->assertStringContainsString('SHA256SUMS.txt', $workflow);
        $this->assertStringContainsString('mapping.txt', $workflow);
        $this->assertStringContainsString('rm -f "$RUNNER_TEMP/safa-release.jks"', $workflow);
    }
}
