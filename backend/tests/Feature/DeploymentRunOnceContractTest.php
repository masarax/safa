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
        $this->assertStringContainsString("storage/run-once.lock", $runner);
        $this->assertStringContainsString("['migrate', ['--force' => true]]", $runner);
        $this->assertStringContainsString("['db:seed', ['--force' => true]]", $runner);
        $this->assertStringContainsString("['optimize:clear', []]", $runner);
        $this->assertStringContainsString("['config:cache', []]", $runner);
        $this->assertStringContainsString("['view:cache', []]", $runner);
        $this->assertStringContainsString('@unlink($runner)', $runner);
        $this->assertStringNotContainsString('migrate:fresh', $runner);
        $this->assertStringNotContainsString('getMessage()', $runner);
    }

    public function test_deploy_workflow_uploads_runner_only_when_explicitly_requested(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));

        $this->assertStringContainsString('upload_run_once:', $workflow);
        $this->assertStringContainsString('default: false', $workflow);
        $this->assertStringContainsString('cpanel_server_dir:', $workflow);
        $this->assertStringContainsString('cp backend/deploy/run-once.php backend/run-once.php', $workflow);
        $this->assertStringContainsString('cp backend/deploy/run-once.php backend/public/run-once.php', $workflow);
        $this->assertStringContainsString('deploy/', $workflow);
        $this->assertStringContainsString('storage/run-once.lock', $workflow);
        $this->assertStringContainsString('run-once.php must be unavailable', $workflow);
        $this->assertStringContainsString('Run mandatory full test suite', $workflow);

        // workflow_dispatch can be launched while another ref is selected even
        // though deployment explicitly checks out main. Production identity must
        // therefore come from the checked-out commit, never the event SHA.
        $this->assertStringContainsString('deploy_sha="$(git rev-parse HEAD)"', $workflow);
        $this->assertStringContainsString('printf \'DEPLOY_SHA=%s\\n\' "$deploy_sha" >> "$GITHUB_ENV"', $workflow);
        $this->assertStringContainsString('EXPECTED_BUILD="$DEPLOY_SHA"', $workflow);
        $this->assertStringNotContainsString('EXPECTED_BUILD="$GITHUB_SHA"', $workflow);
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
