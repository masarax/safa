<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\SafaApiKey;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class AutomaticAccountLoginTest extends TestCase
{
    use RefreshDatabase;

    private function signedHeaders(string $apiKey, string $apiSecret, array $payload, string $method, string $path): array
    {
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $timestamp = (string) time();
        $nonce = 'auto_account_' . bin2hex(random_bytes(12));

        return [
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => hash_hmac('sha256', $method . $path . $timestamp . $nonce . $body, $apiSecret),
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ];
    }

    public function test_login_binds_owned_account_and_account_bootstrap_needs_no_preselected_context(): void
    {
        $user = User::create([
            'name' => 'Automatic Account User',
            'email' => 'automatic-account@safa.local',
            'mobile' => '01700000010',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'admin',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);
        $account = Account::create([
            'name' => 'Automatic Account',
            'owner_user_id' => $user->id,
            'balance' => 0,
        ]);

        $apiKey = 'automatic_account_key';
        $apiSecret = 'automatic_account_secret';
        SafaApiKey::create([
            'account_id' => $account->id,
            'client_name' => 'Automatic Account Test',
            'api_key' => $apiKey,
            'api_secret' => $apiSecret,
            'is_active' => true,
        ]);

        $loginPayload = [
            'mobile' => $user->mobile,
            'pin' => '123456',
            'device_uuid' => 'automatic-device',
            'fingerprint_hash' => 'automatic-fingerprint',
        ];

        $login = $this->postJson('/api/auth/login', $loginPayload, ['Accept' => 'application/json'])
            ->assertOk()
            ->assertJsonPath('active_account_id', $account->id);

        $headers = $this->signedHeaders($apiKey, $apiSecret, [], 'GET', '/api/accounts');
        $headers['Authorization'] = 'Bearer ' . $login->json('access_token');
        $headers['X-SAFA-DEVICE-TOKEN'] = 'automatic-device';
        $headers['X-SAFA-REFRESH-TOKEN'] = $login->json('refresh_token');
        $headers['X-SAFA-SESSION-TOKEN'] = $login->json('session_token');
        $headers['X-SAFA-FINGERPRINT-TOKEN'] = 'automatic-fingerprint';

        $this->withHeaders($headers)
            ->get('/api/accounts')
            ->assertOk()
            ->assertJsonPath('active_account_id', $account->id)
            ->assertJsonPath('owned_account_id', $account->id)
            ->assertJsonPath('accounts.0.account_id', $account->id);
    }

    public function test_login_provisions_one_owned_account_when_user_has_no_account(): void
    {
        $user = User::create([
            'name' => 'Accountless User',
            'email' => 'accountless@safa.local',
            'mobile' => '01700000011',
            'pin_hash' => Hash::make('654321'),
            'password' => Hash::make('654321'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        $response = $this->postJson('/api/auth/login', [
            'mobile' => $user->mobile,
            'pin' => '654321',
            'device_uuid' => 'accountless-device',
            'fingerprint_hash' => 'accountless-fingerprint',
        ], ['Accept' => 'application/json'])->assertOk();

        $accountId = (int) $response->json('active_account_id');
        $this->assertGreaterThan(0, $accountId);
        $this->assertDatabaseHas('accounts', [
            'id' => $accountId,
            'owner_user_id' => $user->id,
        ]);
        $this->assertSame(1, Account::query()->where('owner_user_id', $user->id)->count());
    }

    public function test_shared_account_never_replaces_users_owned_login_account(): void
    {
        $owner = User::create([
            'name' => 'Sharing Owner',
            'email' => 'sharing-owner@safa.local',
            'mobile' => '01700000012',
            'pin_hash' => Hash::make('111111'),
            'password' => Hash::make('111111'),
            'role' => 'admin',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);
        $recipient = User::create([
            'name' => 'Shared Recipient',
            'email' => 'shared-recipient@safa.local',
            'mobile' => '01700000013',
            'pin_hash' => Hash::make('222222'),
            'password' => Hash::make('222222'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);
        $sharedAccount = Account::create([
            'name' => 'Owner Shared Business',
            'owner_user_id' => $owner->id,
            'balance' => 0,
        ]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'account_id' => $sharedAccount->id,
            'shared_with_user_id' => $recipient->id,
            'permissions_override' => ['can_view_customers' => true],
        ]);

        $login = $this->postJson('/api/auth/login', [
            'mobile' => $recipient->mobile,
            'pin' => '222222',
            'device_uuid' => 'shared-recipient-device',
            'fingerprint_hash' => 'shared-recipient-fingerprint',
        ], ['Accept' => 'application/json'])->assertOk();

        $ownedAccountId = (int) $login->json('active_account_id');
        $this->assertNotSame((int) $sharedAccount->id, $ownedAccountId);
        $this->assertDatabaseHas('accounts', [
            'id' => $ownedAccountId,
            'owner_user_id' => $recipient->id,
        ]);

        $accountRequest = \Illuminate\Http\Request::create('/api/accounts', 'GET');
        $accountRequest->setUserResolver(fn () => $recipient);
        $payload = app(\App\Http\Controllers\AccountContextController::class)
            ->index($accountRequest)
            ->getData(true);

        $this->assertSame($ownedAccountId, (int) $payload['active_account_id']);
        $this->assertSame($ownedAccountId, (int) $payload['owned_account_id']);
        $this->assertSame(
            [$ownedAccountId, (int) $sharedAccount->id],
            array_map('intval', array_column($payload['accounts'], 'account_id'))
        );
    }
}
