<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\AuthSession;
use App\Models\User;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class MobileLoginCompatibilityTest extends TestCase
{
    use RefreshDatabase;

    private function seedUser(): User
    {
        $user = User::create([
            'name' => 'Mobile Login User',
            'email' => 'mobile-login@safa.local',
            'mobile' => '0536308965',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'superadmin',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);

        Account::create([
            'name' => 'Mobile Login Account',
            'owner_user_id' => $user->id,
            'balance' => 0,
        ]);

        return $user;
    }

    private function payload(string $device = 'compat-device'): array
    {
        return [
            'mobile' => '0536308965',
            'pin' => '123456',
            'device_uuid' => $device,
            'fingerprint_hash' => 'compat-fingerprint',
        ];
    }

    public function test_versioned_android_login_alias_uses_the_canonical_login_contract(): void
    {
        $user = $this->seedUser();

        $response = $this->postJson('/api/v1/auth/login', $this->payload());

        $response
            ->assertOk()
            ->assertJsonPath('status', 'success')
            ->assertJsonPath('user.id', $user->id)
            ->assertJsonPath('user.mobile', '0536308965');

        $this->assertNotEmpty($response->json('tokens.access_token'));
        $this->assertSame($response->json('access_token'), $response->json('tokens.access_token'));
    }

    public function test_mobile_login_remains_available_while_token_hash_migration_is_pending(): void
    {
        $user = $this->seedUser();

        // Reproduce the production rollout window where application files have
        // been deployed but the token-hash hardening migration has not yet run.
        Schema::drop('auth_sessions');
        Schema::create('auth_sessions', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained('users')->onDelete('cascade');
            $table->string('device_uuid')->index();
            $table->text('access_token');
            $table->string('refresh_token')->index();
            $table->string('session_token')->index();
            $table->timestamp('expires_at')->nullable();
            $table->boolean('is_revoked')->default(false);
            $table->timestamps();
            $table->index(['user_id', 'device_uuid']);
        });

        $response = $this->postJson('/api/auth/login', $this->payload('pre-migration-device'));

        $response
            ->assertOk()
            ->assertJsonPath('status', 'success')
            ->assertJsonPath('user.id', $user->id);

        $accessToken = (string) $response->json('tokens.access_token');
        $this->assertNotSame('', $accessToken);
        $this->assertNotNull(AuthSession::findActiveByAccessToken($accessToken, $user->id));
        $this->assertFalse(AuthSession::supportsTokenHashes());
    }
}
