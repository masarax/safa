<?php

namespace Tests\Feature;

use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\MobileLoginController;
use App\Models\DeviceBinding;
use App\Models\OperatorAccount;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Route;
use Tests\TestCase;

class Phase32AuthenticationContractTest extends TestCase
{
    use RefreshDatabase;

    public function test_active_login_route_has_one_mobile_authentication_source_of_truth(): void
    {
        $loginRoute = collect(Route::getRoutes()->getRoutes())
            ->first(fn ($candidate) => in_array('POST', $candidate->methods()) && $candidate->uri() === 'api/auth/login');

        $this->assertNotNull($loginRoute);
        $this->assertSame(MobileLoginController::class, $loginRoute->getControllerClass());
        $this->assertSame('login', $loginRoute->getActionMethod());

        foreach (Route::getRoutes()->getRoutes() as $candidate) {
            $this->assertStringNotContainsString(
                AuthJWTController::class . '@login',
                $candidate->getActionName(),
                'AuthJWTController::login must not be registered as a production login route.'
            );
        }
    }

    public function test_duplicate_legacy_mobile_identity_is_rejected_instead_of_guessed(): void
    {
        OperatorAccount::create([
            'name' => 'Legacy One',
            'email' => 'legacy-one@safa.local',
            'mobile' => '01912345678',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);
        OperatorAccount::create([
            'name' => 'Legacy Two',
            'email' => 'legacy-two@safa.local',
            'mobile' => '019123-45678',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);

        $this->postJson('/api/auth/login', [
            'mobile' => '01912345678',
            'pin' => '123456',
            'device_uuid' => 'duplicate-device',
            'fingerprint_hash' => 'duplicate-fingerprint',
        ])->assertStatus(409);

        $this->assertDatabaseCount('users', 0);
    }

    public function test_missing_and_invalid_mobile_are_validation_errors(): void
    {
        $this->postJson('/api/auth/login', [
            'pin' => '123456',
            'device_uuid' => 'validation-device',
            'fingerprint_hash' => 'validation-fingerprint',
        ])->assertStatus(422);

        $this->postJson('/api/auth/login', [
            'mobile' => '12345',
            'pin' => '123456',
            'device_uuid' => 'validation-device-2',
            'fingerprint_hash' => 'validation-fingerprint-2',
        ])->assertStatus(422);
    }

    public function test_revoked_device_is_rejected_before_session_creation(): void
    {
        $user = User::create([
            'name' => 'Revoked Device User',
            'email' => 'revoked@safa.local',
            'mobile' => '0536308965',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => 'revoked-device',
            'device_model' => 'Test Device',
            'fingerprint_hash' => 'revoked-fingerprint',
            'is_active' => false,
            'bound_at' => now(),
        ]);

        $this->postJson('/api/auth/login', [
            'mobile' => '0536308965',
            'pin' => '123456',
            'device_uuid' => 'revoked-device',
            'fingerprint_hash' => 'revoked-fingerprint',
        ])->assertStatus(403);
    }
}
