<?php

namespace Tests\Feature;

use Tests\TestCase;

class AndroidReleaseProvenanceTest extends TestCase
{
    public function test_signing_requires_exact_green_android_ci_before_secrets_are_used(): void
    {
        $workflow = (string) file_get_contents(base_path('../.github/workflows/release-apk.yml'));
        $script = (string) file_get_contents(base_path('../scripts/verify-android-release-eligibility.sh'));

        $this->assertStringContainsString('actions: read', $workflow);
        $this->assertStringContainsString('fetch-depth: 0', $workflow);
        $this->assertStringContainsString('scripts/verify-android-release-eligibility.sh', $workflow);
        $this->assertStringContainsString('validated_android_ci_run=', $script);
        $this->assertStringContainsString('head_sha=${GITHUB_SHA}', $script);
        $this->assertStringContainsString('Android Production CI', $script);
        $this->assertStringContainsString('Unit, lint and release build', $script);
        $this->assertStringContainsString('Emulator instrumentation and release smoke', $script);
        $this->assertStringContainsString('git merge-base --is-ancestor "$GITHUB_SHA" refs/remotes/origin/main', $script);
        $this->assertStringContainsString('expected_tag="v${version_name}"', $script);

        $provenancePosition = strpos($workflow, 'Require exact full Android Production CI success');
        $secretPosition = strpos($workflow, 'Validate signing secrets');
        $this->assertIsInt($provenancePosition);
        $this->assertIsInt($secretPosition);
        $this->assertLessThan($secretPosition, $provenancePosition);

        $this->assertStringContainsString('validated_android_ci_run', $workflow);
        $this->assertStringContainsString('build-identity.txt', $workflow);
    }
}
