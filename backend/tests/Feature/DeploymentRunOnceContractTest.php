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

    public function test_deploy_workflow_uses_direct_cpanel_git_api_without_transfer_path_inputs(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/deploy.yml'));

        $this->assertStringContainsString('upload_run_once:', $workflow);
        $this->assertStringContainsString('default: false', $workflow);
        $this->assertStringContainsString('CPANEL_API_URL', $workflow);
        $this->assertStringContainsString('CPANEL_USERNAME', $workflow);
        $this->assertStringContainsString('CPANEL_API_TOKEN', $workflow);
        $this->assertStringContainsString('Authorization: cpanel', $workflow);
        $normalized = str_replace('/', ' ', $workflow);
        $this->assertStringContainsString('VersionControl create', $normalized);
        $this->assertStringContainsString('VersionControl update', $normalized);
        $this->assertStringContainsString('VersionControlDeployment create', $normalized);
        $this->assertStringContainsString('VersionControlDeployment retrieve', $normalized);
        $this->assertStringContainsString('Fileman save_file_content', $normalized);
        $this->assertStringContainsString('/home/$CPANEL_USERNAME/repositories/safa', $workflow);
        $this->assertStringContainsString('CPANEL_DEPLOY_ID', $workflow);
        $this->assertStringContainsString('cPanel deployment log:', $workflow);
        $this->assertStringContainsString('Run mandatory full test suite', $workflow);
        $this->assertStringContainsString('bash -n deploy/cpanel-deploy.sh', $workflow);

        // The workflow always deploys the checked-out main commit, requires the
        // cPanel task to succeed for that SHA, then verifies the live exact build.
        $this->assertStringContainsString('deploy_sha="$(git rev-parse HEAD)"', $workflow);
        $this->assertStringContainsString('$task_identifier" != "$DEPLOY_SHA', $workflow);
        $this->assertStringContainsString('EXPECTED_BUILD="$DEPLOY_SHA"', $workflow);
        $this->assertStringNotContainsString('EXPECTED_BUILD="$GITHUB_SHA"', $workflow);

        // Deployment configuration must not expose or depend on a file-transfer
        // server/path mechanism. cPanel Git/UAPI owns the deployment transport.
        $this->assertStringNotContainsString('cpanel_server_dir', $workflow);
        $this->assertStringNotContainsString('SamKirkland', $workflow);
        $this->assertStringNotContainsString('FTP_', $workflow);
        $this->assertStringNotContainsString('ftp', strtolower($workflow));
    }

    public function test_cpanel_deployment_script_is_safe_idempotent_and_runs_required_setup(): void
    {
        $cpanel = (string) file_get_contents(base_path('../.cpanel.yml'));
        $script = (string) file_get_contents(base_path('deploy/cpanel-deploy.sh'));

        $this->assertStringContainsString('/bin/bash backend/deploy/cpanel-deploy.sh', $cpanel);
        $this->assertStringContainsString('DomainInfo single_domain_data', $script);
        $this->assertStringContainsString('bootstrap/safa-build.json', $script);
        $this->assertStringContainsString('.safa-deployed-files', $script);
        $this->assertStringContainsString('--exclude=\'./.env\'', $script);
        $this->assertStringContainsString('--exclude=\'./storage\'', $script);
        $this->assertStringContainsString('storage/framework/cache/data', $script);
        $this->assertStringContainsString('migrate --force --no-interaction', $script);
        $this->assertStringContainsString('db:seed --force --no-interaction', $script);
        $this->assertStringContainsString('optimize:clear', $script);
        $this->assertStringContainsString('config:cache', $script);
        $this->assertStringContainsString('view:cache', $script);
        $this->assertStringContainsString('$PROJECT_ROOT/run-once.php', $script);
        $this->assertStringContainsString('$PROJECT_ROOT/public/run-once.php', $script);
        $this->assertStringContainsString('RUNNER_LOCK="$PROJECT_ROOT/storage/run-once.lock"', $script);
        $this->assertStringContainsString('! -f "$RUNNER_LOCK"', $script);
        $this->assertStringContainsString('[[ -f "$PROJECT_ROOT/.env" ]]', $script);
        $this->assertStringNotContainsString('migrate:fresh', $script);
        $this->assertStringNotContainsString('rm -rf "$PROJECT_ROOT"', $script);
        $this->assertStringNotContainsString('ftp', strtolower($script));
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
