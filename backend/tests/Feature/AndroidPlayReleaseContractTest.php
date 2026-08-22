<?php

namespace Tests\Feature;

use Tests\TestCase;

class AndroidPlayReleaseContractTest extends TestCase
{
    public function test_release_workflow_builds_and_verifies_apk_and_aab_from_same_validated_sha(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/release-apk.yml'));

        $this->assertStringContainsString('verify-android-release-eligibility.sh', $workflow);
        $this->assertStringContainsString('assembleRelease bundleRelease', $workflow);
        $this->assertStringContainsString("-name '*.aab'", $workflow);
        $this->assertStringContainsString('apksigner verify --verbose --print-certs', $workflow);
        $this->assertStringContainsString('jarsigner -verify -strict -certs', $workflow);
        $this->assertStringContainsString('app/build/outputs/bundle/release/*.aab', $workflow);
        $this->assertStringContainsString('safa-android.cdx.json', $workflow);
        $this->assertStringContainsString('SHA256SUMS.txt', $workflow);
        $this->assertStringContainsString('build-identity.txt', $workflow);
        $this->assertStringContainsString('versionCode=', $workflow);
        $this->assertStringContainsString('versionName=', $workflow);
        $this->assertStringContainsString('signing_certificate_', $workflow);
    }

    public function test_play_release_runbook_requires_staged_human_approved_promotion_and_key_recovery(): void
    {
        $runbook = (string) file_get_contents(base_path('../docs/ANDROID_PLAY_RELEASE.md'));

        $this->assertStringContainsString('com.safa.account', $runbook);
        $this->assertStringContainsString('Play App Signing', $runbook);
        $this->assertStringContainsString('upload-key reset', $runbook);
        $this->assertStringContainsString('Internal testing', $runbook);
        $this->assertStringContainsString('Closed testing', $runbook);
        $this->assertStringContainsString('Production staged rollout', $runbook);
        $this->assertStringContainsString('not automated', $runbook);
        $this->assertStringContainsString('versionCode', $runbook);
        $this->assertStringContainsString('mapping.txt', $runbook);
        $this->assertStringContainsString('targetSdk', $runbook);
        $this->assertStringContainsString('checksums', strtolower($runbook));
    }
}
