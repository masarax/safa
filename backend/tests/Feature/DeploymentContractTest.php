<?php

namespace Tests\Feature;

use Tests\TestCase;

class DeploymentContractTest extends TestCase
{
    public function test_cpanel_workflow_deploys_exact_green_backend_ci_sha_only(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));

        $this->assertStringNotContainsString("workflow_dispatch:\n", $workflow);
        $this->assertStringContainsString("workflow_run:\n", $workflow);
        $this->assertStringContainsString('workflows: ["Laravel Backend CI"]', $workflow);
        $this->assertStringContainsString('types: [completed]', $workflow);
        $this->assertStringContainsString('branches: [main]', $workflow);
        $this->assertStringContainsString("github.event.workflow_run.conclusion == 'success'", $workflow);
        $this->assertStringContainsString("github.event.workflow_run.event == 'push'", $workflow);
        $this->assertStringContainsString('github.event.workflow_run.head_repository.full_name == github.repository', $workflow);
        $this->assertStringContainsString('github.event.workflow_run.head_branch == github.event.repository.default_branch', $workflow);
        $this->assertStringContainsString('ref: ${{ github.event.workflow_run.head_sha }}', $workflow);
        $this->assertStringContainsString('TESTED_SHA: ${{ github.event.workflow_run.head_sha }}', $workflow);
        $this->assertStringContainsString('test "$CHECKED_OUT_SHA" = "$TESTED_SHA"', $workflow);
        $this->assertStringContainsString('test "$CURRENT_DEFAULT_SHA" = "$TESTED_SHA"', $workflow);
        $this->assertStringContainsString('test "$CURRENT_DEFAULT_SHA" = "$DEPLOY_SHA"', $workflow);
        $this->assertStringContainsString('test "$(git rev-parse HEAD)" = "$DEPLOY_SHA"', $workflow);
        $this->assertStringContainsString('printf \'{"commit":"%s"}\\n\' "$DEPLOY_SHA" > backend/bootstrap/safa-build.json', $workflow);
        $this->assertStringNotContainsString('ref: main', $workflow);
        $this->assertStringContainsString('cancel-in-progress: false', $workflow);
        $this->assertStringContainsString('bash scripts/check-deploy-provenance.sh', $workflow);
        $this->assertStringContainsString('composer install --no-dev --optimize-autoloader --no-interaction --prefer-dist --no-progress', $workflow);
        $this->assertStringContainsString('actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09 # v5.1.0', $workflow);
        $this->assertStringContainsString('shivammathur/setup-php@f3e473d116dcccaddc5834248c87452386958240 # v2.37.2', $workflow);
        $this->assertStringContainsString('SamKirkland/FTP-Deploy-Action@8e83cea8672e3fbcbb9fdafff34debf6ae4c5f65 # v4.3.5', $workflow);
        $this->assertStringContainsString('local-dir: backend/', $workflow);
        $this->assertStringContainsString('server-dir: /', $workflow);

        preg_match_all('/^[ \t]*uses:[ \t]+[^\s#]+@([^\s#]+)/m', $workflow, $actionRefs);
        foreach ($actionRefs[1] ?? [] as $actionRef) {
            $this->assertMatchesRegularExpression('/^[0-9a-f]{40}$/i', $actionRef);
        }

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

    public function test_database_update_is_owned_by_one_click_release_gate_and_safe_update_service(): void
    {
        $controller = (string) file_get_contents(app_path('Http/Controllers/ReleaseUpdateController.php'));
        $service = (string) file_get_contents(app_path('Services/DatabaseUpdateService.php'));
        $middleware = (string) file_get_contents(app_path('Http/Middleware/CheckInstalled.php'));
        $webRoutes = (string) file_get_contents(base_path('routes/web.php'));
        $setupRoutes = (string) file_get_contents(base_path('routes/setup.php'));
        $publicHtaccess = (string) file_get_contents(public_path('.htaccess'));

        $this->assertStringContainsString("Route::get('/update'", $setupRoutes);
        $this->assertStringContainsString("Route::post('/update/run'", $setupRoutes);
        $this->assertStringContainsString("name('system.update.show')", $setupRoutes);
        $this->assertStringContainsString("name('system.update.run')", $setupRoutes);
        $this->assertStringNotContainsString("Route::get('/update'", $webRoutes);
        $this->assertStringContainsString("'/system/update'", $webRoutes);
        $this->assertStringNotContainsString("Route::get('/system/update'", $webRoutes);
        $this->assertStringNotContainsString("Route::post('/system/update/run'", $webRoutes);

        $this->assertStringContainsString('ReleaseUpdateState::required()', $controller);
        $this->assertStringContainsString("redirect()->route('safa.login'", $controller);
        $this->assertStringNotContainsString('Maintenance key', $controller);
        $this->assertStringContainsString("Artisan::call('migrate', ['--force' => true])", $service);
        $this->assertStringContainsString('ReleaseDataUpdateSeeder::class', $service);
        $this->assertStringContainsString("Artisan::call('optimize:clear')", $service);
        $this->assertStringContainsString('flock($handle, LOCK_EX | LOCK_NB)', $service);
        $this->assertStringContainsString("redirect()->route('system.update.show')", $middleware);
        $this->assertStringContainsString("'status' => 'update_required'", $middleware);
        $this->assertStringNotContainsString("config('safa.installed'", $middleware);
        $this->assertStringNotContainsString("storage_path('installed')", $middleware);

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
