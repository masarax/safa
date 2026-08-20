<?php

namespace Tests\Feature;

use App\Http\Controllers\AccountContextController;
use Illuminate\Http\Request;
use Tests\TestCase;

class LegacyAuthRouteHardeningTest extends TestCase
{
    public function test_legacy_share_alias_uses_canonical_account_share_controller(): void
    {
        $router = app('router');
        $legacy = $router->getRoutes()->match(Request::create('/api/auth/share-account', 'POST'));
        $canonical = $router->getRoutes()->match(Request::create('/api/accounts/share', 'POST'));

        $this->assertSame(AccountContextController::class . '@share', $legacy->getActionName());
        $this->assertSame(AccountContextController::class . '@share', $canonical->getActionName());
    }

    public function test_public_legacy_bind_device_route_is_removed(): void
    {
        $this->postJson('/api/auth/bind-device', [
            'mobile' => '0500000000',
            'pin' => '123456',
            'device_uuid' => 'legacy-device',
            'fingerprint_hash' => 'legacy-fingerprint',
        ])->assertNotFound();
    }
}
