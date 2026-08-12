<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class SecurityHardeningTest extends TestCase
{
    use RefreshDatabase;

    public function test_auth_session_tokens_are_encrypted_and_indexed_by_hash(): void
    {
        $user = User::create([
            'name' => 'Security Test', 'email' => 'security@safa.local', 'mobile' => '01700000999', 'role' => 'staff',
            'pin_hash' => Hash::make('123456'), 'password' => Hash::make('123456'), 'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        $access = 'access-' . bin2hex(random_bytes(16));
        $refresh = 'refresh-' . bin2hex(random_bytes(16));
        $sessionToken = 'session-' . bin2hex(random_bytes(16));
        $session = AuthSession::create([
            'user_id' => $user->id, 'device_uuid' => 'device-test', 'access_token' => $access,
            'refresh_token' => $refresh, 'session_token' => $sessionToken, 'expires_at' => now()->addHour(), 'is_revoked' => false,
        ]);

        $raw = $this->getConnection()->table('auth_sessions')->where('id', $session->id)->first();
        $this->assertNotSame($access, $raw->access_token);
        $this->assertSame(hash('sha256', $access), $raw->access_token_hash);
        $this->assertSame(hash('sha256', $refresh), $raw->refresh_token_hash);
        $this->assertSame(hash('sha256', $sessionToken), $raw->session_token_hash);
    }

    public function test_shared_user_cannot_cross_access_another_account_of_same_owner(): void
    {
        $owner = User::create(['name' => 'Owner', 'email' => 'owner@safa.local', 'mobile' => '01700000001', 'role' => 'staff', 'pin_hash' => Hash::make('123456'), 'password' => Hash::make('123456'), 'is_activated' => true, 'permissions' => User::defaultPermissions(false)]);
        $shared = User::create(['name' => 'Shared', 'email' => 'shared@safa.local', 'mobile' => '01700000002', 'role' => 'staff', 'pin_hash' => Hash::make('123456'), 'password' => Hash::make('123456'), 'is_activated' => true, 'permissions' => User::defaultPermissions(false)]);
        $accountA = Account::create(['name' => 'Account A', 'owner_user_id' => $owner->id, 'balance' => 0]);
        $accountB = Account::create(['name' => 'Account B', 'owner_user_id' => $owner->id, 'balance' => 0]);
        UserAccountShare::create(['owner_user_id' => $owner->id, 'account_id' => $accountA->id, 'shared_with_user_id' => $shared->id, 'permissions_override' => []]);

        $this->actingAs($shared);
        $request = request();
        $request->headers->set('X-SAFA-ACCOUNT-ID', (string) $accountB->id);

        $trait = new class { use \App\Http\Controllers\AuthorizeAccountContext; };
        $result = $trait->resolveAuthorizedAccountContext($request);
        $this->assertArrayHasKey('error', $result);
    }
}
