<?php

namespace Tests\Feature;

use App\Http\Controllers\RemoteConfigController;
use App\Models\AppVersion;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class AppVersionPolicyTest extends TestCase
{
    use RefreshDatabase;

    public function test_current_and_newer_builds_are_up_to_date(): void
    {
        $this->version(1, 2, false);

        foreach ([2, 3] as $code) {
            $payload = $this->check($code);
            $this->assertFalse($payload['update_available']);
            $this->assertFalse($payload['force_update']);
            $this->assertSame('App is up to date.', $payload['message']);
        }
    }

    public function test_older_supported_build_gets_optional_update(): void
    {
        $this->version(1, 2, false);
        $payload = $this->check(1);

        $this->assertTrue($payload['update_available']);
        $this->assertFalse($payload['force_update']);
        $this->assertSame(2, $payload['latest_version_code']);
        $this->assertSame('An optional update is available.', $payload['message']);
    }

    public function test_build_below_minimum_gets_mandatory_update(): void
    {
        $this->version(2, 3, false);
        $payload = $this->check(1);

        $this->assertTrue($payload['update_available']);
        $this->assertTrue($payload['force_update']);
        $this->assertSame('A mandatory update is required to continue.', $payload['message']);
    }

    public function test_force_flag_does_not_force_current_build(): void
    {
        $this->version(1, 2, true);

        $old = $this->check(1);
        $this->assertTrue($old['force_update']);

        $current = $this->check(2);
        $this->assertFalse($current['update_available']);
        $this->assertFalse($current['force_update']);
    }

    private function version(int $minimum, int $latest, bool $force): void
    {
        AppVersion::create([
            'platform' => 'android',
            'min_version_code' => $minimum,
            'latest_version_code' => $latest,
            'force_update' => $force,
            'update_url' => null,
        ]);
    }

    private function check(int $versionCode): array
    {
        $request = Request::create('/version-check-test', 'GET', ['version_code' => $versionCode]);
        return app(RemoteConfigController::class)->checkVersion($request)->getData(true);
    }
}
