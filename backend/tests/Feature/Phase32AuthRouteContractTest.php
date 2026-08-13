<?php

namespace Tests\Feature;

use App\Http\Controllers\MobileLoginController;
use Illuminate\Support\Facades\Route;
use Tests\TestCase;

class Phase32AuthRouteContractTest extends TestCase
{
    public function test_production_login_route_uses_canonical_mobile_login_controller(): void
    {
        $route = Route::getRoutes()->match(
            request()->create('/api/auth/login', 'POST')
        );

        $this->assertSame(MobileLoginController::class, $route->getControllerClass());
        $this->assertSame('login', $route->getActionMethod());
    }
}
