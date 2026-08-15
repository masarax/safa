<?php

namespace Tests\Feature;

use Tests\TestCase;

class DeploymentRunOnceContractTest extends TestCase
{
    public function test_run_once_runner_requires_secret_lock_and_self_deletion(): void
    {
        $runner = (string) file_get_contents(base_path('deploy/run-once.php'));

        $this->assertStringContainsString("env('SAFA_RUN_ONCE_TOKEN'", $runner);
        $this->assertStringContainsString('hash_equals($expectedToken, $providedToken)', $runner);
        $this->assertStringContainsString('storage/run-once.lock', $runner);
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
