<?php

namespace Tests\Feature;

use Tests\TestCase;

class DeploymentRunOnceContractTest extends TestCase
{
    public function test_run_once_runner_requires_secret_lock_and_self_deletion(): void
    {
        $runner = (string) file_get_contents(base_path('deploy/run-once.php'));
        $config = (string) file_get_contents(config_path('safa.php'));

        $this->assertStringContainsString("'run_once_token' => env('SAFA_RUN_ONCE_TOKEN')", $config);
        $this->assertStringContainsString("config('safa.run_once_token'", $runner);
        $this->assertStringNotContainsString("env('SAFA_RUN_ONCE_TOKEN'", $runner);
        $this->assertStringContainsString('hash_equals($expectedToken, $providedToken)', $runner);
        $this->assertStringContainsString('storage/run-once.lock', $runner);
        $this->assertStringContainsString('$cleanupRunners = static function () use ($runnerCopies): void', $runner);
        $this->assertStringContainsString('if (is_file($lockPath)) {', $runner);
        $this->assertStringContainsString("if (\$expectedToken === '') {", $runner);
        $this->assertStringContainsString("['migrate', ['--force' => true]]", $runner);
        $this->assertStringContainsString("['db:seed', ['--force' => true]]", $runner);
        $this->assertStringContainsString("['optimize:clear', []]", $runner);
        $this->assertStringContainsString("['config:cache', []]", $runner);
        $this->assertStringContainsString("['view:cache', []]", $runner);
        $this->assertStringContainsString('@unlink($runner)', $runner);
        $this->assertStringNotContainsString('migrate:fresh', $runner);
        $this->assertStringNotContainsString('getMessage()', $runner);
    }

    public function test_deploy_workflow_has_no_dispatch_options_and_uses_only_existing_ftp_secrets(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));

        $this->assertStringContainsString("workflow_dispatch:\n", $workflow);
        $this->assertStringNotContainsString('inputs:', $workflow);
        $this->assertStringNotContainsString('upload_run_once', $workflow);
        $this->assertStringNotContainsString('cpanel_server_dir', $workflow);
        $this->assertStringNotContainsString('CPANEL_API_', $workflow);
        $this->assertStringNotContainsString('PRODUCTION_BASE_URL', $workflow);

        preg_match_all('/secrets\.([A-Z0-9_]+)/', $workflow, $matches);
        $secrets = array_values(array_unique($matches[1] ?? []));
        sort($secrets);

        $this->assertSame([
            'FTP_PASSWORD',
            'FTP_SERVER',
            'FTP_USERNAME',
        ], $secrets);

        $this->assertStringContainsString('SamKirkland/FTP-Deploy-Action@v4.3.5', $workflow);
        $this->assertStringContainsString('server: ${{ secrets.FTP_SERVER }}', $workflow);
        $this->assertStringContainsString('username: ${{ secrets.FTP_USERNAME }}', $workflow);
        $this->assertStringContainsString('password: ${{ secrets.FTP_PASSWORD }}', $workflow);
        $this->assertStringContainsString('local-dir: backend/', $workflow);
        $this->assertStringContainsString('server-dir: /', $workflow);
        $this->assertStringContainsString('public/storage/logos/**', $workflow);
        $this->assertStringContainsString('Run mandatory full test suite', $workflow);
    }

    public function test_deploy_stages_only_the_secure_browser_setup_copies(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));
        $rootHtaccess = (string) file_get_contents(base_path('.htaccess'));
        $publicHtaccess = (string) file_get_contents(public_path('.htaccess'));
        $envExample = (string) file_get_contents(base_path('.env.example'));

        $this->assertStringContainsString('cp backend/deploy/run-once.php backend/run-once.php', $workflow);
        $this->assertStringContainsString('cp backend/deploy/run-once.php backend/public/run-once.php', $workflow);
        $this->assertStringContainsString("            deploy/\n", $workflow);
        $this->assertStringContainsString('(app|bootstrap|config|database|deploy|public|resources|routes|storage|tests|vendor|', $rootHtaccess);
        $this->assertStringContainsString('RewriteRule ^run-once\\.php$ - [L,NC]', $publicHtaccess);
        $this->assertStringContainsString('if this value is empty the runner returns 404 and deletes itself', $envExample);
        $this->assertStringNotContainsString('upload_run_once=true', $envExample);
    }

    public function test_ftp_deploy_applies_pending_migrations_with_ephemeral_self_deleting_runner(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));
        $publicHtaccess = (string) file_get_contents(public_path('.htaccess'));
        $controller = (string) file_get_contents(app_path('Http/Controllers/DatabaseUpdateController.php'));

        $this->assertStringContainsString('openssl rand -hex 16', $workflow);
        $this->assertStringContainsString('openssl rand -hex 32', $workflow);
        $this->assertStringContainsString('X-SAFA-Deploy-Token', $workflow);
        $this->assertStringContainsString("Artisan::call('migrate', ['--force' => true])", $workflow);
        $this->assertStringContainsString("Artisan::call('optimize:clear')", $workflow);
        $this->assertStringContainsString("Artisan::call('config:cache')", $workflow);
        $this->assertStringContainsString("Artisan::call('view:cache')", $workflow);
        $this->assertStringContainsString('pending_count', $workflow);
        $this->assertStringContainsString('@unlink($runner)', $workflow);
        $this->assertStringContainsString("test \"\$deleted_code\" = '404'", $workflow);
        $this->assertStringContainsString('https://safa.masarax.com/login', $workflow);
        $this->assertStringContainsString('https://safa.masarax.com/api/auth/health', $workflow);
        $this->assertStringContainsString('SAFA_DEPLOY_SHA', $workflow);
        $this->assertStringNotContainsString('migrate:fresh', $workflow);

        $this->assertStringContainsString(
            'RewriteRule ^safa-deploy-migrate-[a-f0-9]{32}\\.php$ - [L,NC]',
            $publicHtaccess
        );
        $this->assertStringContainsString('2026_08_12_000001_harden_auth_session_storage', $controller);
        $this->assertStringContainsString("'auth_sessions' => ['access_token_hash', 'refresh_token_hash', 'session_token_hash']", $controller);
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
