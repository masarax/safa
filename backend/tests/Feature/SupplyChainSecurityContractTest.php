<?php

namespace Tests\Feature;

use Tests\TestCase;

class SupplyChainSecurityContractTest extends TestCase
{
    public function test_supply_chain_controls_are_committed_and_fail_closed(): void
    {
        $root = dirname(base_path());
        $securityWorkflow = (string) file_get_contents($root . '/.github/workflows/security-ci.yml');
        $backendWorkflow = (string) file_get_contents($root . '/.github/workflows/backend-ci.yml');
        $releaseWorkflow = (string) file_get_contents($root . '/.github/workflows/release-apk.yml');
        $dependabot = (string) file_get_contents($root . '/.github/dependabot.yml');
        $androidBuild = (string) file_get_contents($root . '/app/build.gradle.kts');
        $allowlist = json_decode((string) file_get_contents($root . '/security/osv-allowlist.json'), true, 32, JSON_THROW_ON_ERROR);

        $this->assertStringContainsString('composer audit --locked --no-dev', $backendWorkflow);
        $this->assertStringContainsString('php scripts/php-security-scan.php', $backendWorkflow);
        $this->assertStringContainsString('scripts/osv-scan.py', $securityWorkflow);
        $this->assertStringContainsString(':app:safaResolvedReleaseDependencies', $securityWorkflow);
        $this->assertStringContainsString('lintRelease', $securityWorkflow);
        $this->assertStringContainsString('scripts/generate-sbom.py', $securityWorkflow);
        $this->assertStringContainsString('safa-android.cdx.json', $releaseWorkflow);
        $this->assertStringContainsString('safaResolvedReleaseDependencies', $androidBuild);
        $this->assertStringContainsString('package-ecosystem: composer', $dependabot);
        $this->assertStringContainsString('package-ecosystem: gradle', $dependabot);
        $this->assertStringContainsString('package-ecosystem: github-actions', $dependabot);
        $this->assertSame(1, $allowlist['schema'] ?? null);
        $this->assertIsArray($allowlist['exceptions'] ?? null);
    }

    public function test_vulnerability_exceptions_have_bounded_expiry_and_rationale(): void
    {
        $allowlist = json_decode(
            (string) file_get_contents(dirname(base_path()) . '/security/osv-allowlist.json'),
            true,
            32,
            JSON_THROW_ON_ERROR,
        );

        foreach ($allowlist['exceptions'] ?? [] as $exception) {
            $this->assertNotSame('', trim((string) ($exception['id'] ?? '')));
            $this->assertNotSame('', trim((string) ($exception['rationale'] ?? '')));
            $expiry = \DateTimeImmutable::createFromFormat('!Y-m-d', (string) ($exception['expires'] ?? ''));
            $this->assertInstanceOf(\DateTimeImmutable::class, $expiry);
            $this->assertGreaterThanOrEqual(new \DateTimeImmutable('today'), $expiry);
        }
    }
}
