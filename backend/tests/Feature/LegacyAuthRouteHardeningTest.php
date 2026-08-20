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
        $matchingRoutes = collect(app('router')->getRoutes()->getRoutes())
            ->filter(fn ($route) =>
                $route->uri() === 'api/auth/bind-device'
                && in_array('POST', $route->methods(), true)
            )
            ->values();

        $this->assertCount(0, $matchingRoutes);
    }
}
