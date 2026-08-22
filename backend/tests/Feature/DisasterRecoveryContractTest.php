<?php

namespace Tests\Feature;

use Tests\TestCase;

class DisasterRecoveryContractTest extends TestCase
{
    public function test_backup_health_is_externally_observable_without_exposing_paths(): void
    {
        $dir = storage_path('framework/testing/dr-' . bin2hex(random_bytes(6)));
        mkdir($dir, 0777, true);
        $full = $dir . '/full.json';
        $binlog = $dir . '/binlog.json';
        $assets = $dir . '/assets.json';

        try {
            foreach ([$full, $binlog, $assets] as $path) {
                file_put_contents($path, json_encode(['completed_at_epoch' => time()], JSON_THROW_ON_ERROR));
            }

            config()->set('safa.dr.status_required', true);
            config()->set('safa.dr.full_status_file', $full);
            config()->set('safa.dr.binlog_status_file', $binlog);
            config()->set('safa.dr.asset_status_file', $assets);
            config()->set('safa.dr.full_max_age_seconds', 93600);
            config()->set('safa.dr.binlog_max_age_seconds', 900);
            config()->set('safa.dr.asset_max_age_seconds', 900);

            $response = $this->getJson('/api/auth/backup-health')
                ->assertOk()
                ->assertJsonPath('status', 'ok')
                ->assertJsonPath('configured', true)
                ->assertJsonPath('checks.full_backup', true)
                ->assertJsonPath('checks.binlog_archive', true)
                ->assertJsonPath('checks.logo_assets', true);

            $payload = $response->json();
            $serialized = json_encode($payload, JSON_THROW_ON_ERROR);
            $this->assertStringNotContainsString($dir, $serialized);
            $this->assertStringNotContainsString('artifact', $serialized);

            file_put_contents($binlog, json_encode(['completed_at_epoch' => time() - 901], JSON_THROW_ON_ERROR));
            $this->getJson('/api/auth/backup-health')
                ->assertStatus(503)
                ->assertJsonPath('status', 'degraded')
                ->assertJsonPath('checks.full_backup', true)
                ->assertJsonPath('checks.binlog_archive', false)
                ->assertJsonPath('checks.logo_assets', true);
        } finally {
            foreach ([$full, $binlog, $assets] as $path) @unlink($path);
            @rmdir($dir);
        }
    }

    public function test_versioned_backup_and_restore_helpers_fail_closed_and_cover_pitr(): void
    {
        $full = (string) file_get_contents(base_path('bin/mysql-full-backup.sh'));
        $binlog = (string) file_get_contents(base_path('bin/mysql-binlog-archive.sh'));
        $assets = (string) file_get_contents(base_path('bin/logo-assets-backup.sh'));
        $restore = (string) file_get_contents(base_path('bin/mysql-restore-pitr.sh'));
        $assetRestore = (string) file_get_contents(base_path('bin/logo-assets-restore.sh'));
        $monitor = (string) file_get_contents(base_path('bin/verify-backup-freshness.sh'));

        $this->assertStringContainsString('SAFA_BACKUP_OFFHOST_ACK', $full);
        $this->assertStringContainsString('openssl enc -aes-256-cbc', $full);
        $this->assertStringContainsString('sha256sum -c', $full);
        $this->assertMatchesRegularExpression('/--(source|master)-data=2/', $full);

        $this->assertStringContainsString('SHOW BINARY LOGS', $binlog);
        $this->assertStringContainsString('--read-from-remote-server', $binlog);
        $this->assertStringContainsString('openssl enc -aes-256-cbc', $binlog);

        $this->assertStringContainsString('public/storage/logos', $assets);
        $this->assertStringContainsString('content_sha256', $assets);
        $this->assertStringContainsString('openssl enc -aes-256-cbc', $assets);

        $this->assertStringContainsString('RESTORE_TO_EMPTY_RECOVERY_DATABASE', $restore);
        $this->assertStringContainsString('SAFA_SOURCE_DB_NAME', $restore);
        $this->assertStringContainsString('SAFA_RECOVERY_DB_NAME', $restore);
        $this->assertStringContainsString('mysqlbinlog', $restore);
        $this->assertStringContainsString('--rewrite-db=', $restore);
        $this->assertStringContainsString('--start-position=', $restore);
        $this->assertStringContainsString('recovery database is not empty', $restore);

        $this->assertStringContainsString('RESTORE_TO_EMPTY_RECOVERY_DIRECTORY', $assetRestore);
        $this->assertStringContainsString('recovery logo directory is not empty', $assetRestore);
        $this->assertStringContainsString('unsafe path in asset archive', $assetRestore);

        $this->assertStringContainsString('SAFA_BACKUP_ALERT_WEBHOOK', $monitor);
        $this->assertStringContainsString('encrypted artifact checksum does not match', $monitor);
        $this->assertStringContainsString("check_status 'logo assets'", $monitor);
    }

    public function test_dr_runbook_and_ci_drill_define_recovery_objectives_and_integrity_evidence(): void
    {
        $runbook = (string) file_get_contents(base_path('../docs/DISASTER_RECOVERY.md'));
        $updatePolicy = (string) file_get_contents(base_path('../docs/DATABASE_UPDATE_POLICY.md'));
        $workflow = (string) file_get_contents(base_path('../.github/workflows/dr-restore-drill.yml'));

        $this->assertStringContainsString('RPO: 15 minutes', $runbook);
        $this->assertStringContainsString('RTO: 4 hours', $runbook);
        $this->assertStringContainsString('cPanel', $runbook);
        $this->assertStringContainsString('35 days', $runbook);
        $this->assertStringContainsString('off-host', strtolower($runbook));
        $this->assertStringContainsString('public/storage/logos', $runbook);
        $this->assertStringContainsString('two authorized operators', strtolower($runbook));
        $this->assertStringContainsString('/api/auth/backup-health', $runbook);

        $this->assertStringContainsString('DISASTER_RECOVERY.md', $updatePolicy);
        $this->assertStringContainsString('verify-backup-freshness.sh', $updatePolicy);
        $this->assertStringContainsString('SAFA_BACKUP_STATUS_REQUIRED=true', $updatePolicy);

        $this->assertStringContainsString('schedule:', $workflow);
        $this->assertStringContainsString('SHOW MASTER STATUS', $workflow);
        $this->assertStringContainsString('mysqlbinlog', $workflow);
        $this->assertStringContainsString('mysql-recovery:', $workflow);
        $this->assertStringContainsString('3307:3306', $workflow);
        $this->assertStringContainsString('same authoritative database name', $workflow);
        $this->assertStringContainsString('CORRUPTED-LATER', $workflow);
        $this->assertStringContainsString('SAFA-DR-PITR', $workflow);
        foreach (['customers', 'suppliers', 'wallet_ledgers', 'wallet_batches', 'transactions', 'system_settings'] as $table) {
            $this->assertStringContainsString($table, $workflow);
        }
        $this->assertStringContainsString('recovery_duration_seconds', $workflow);
        $this->assertStringContainsString('actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02', $workflow);
    }
}
