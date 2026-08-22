<?php

namespace Tests\Feature;

use App\Http\Controllers\AuthorizeAccountContext;
use App\Http\Middleware\AuditLogMiddleware;
use App\Models\Account;
use App\Models\AuditLog;
use App\Models\DeviceBinding;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;

class AuditSecurityRegressionTest extends TestCase
{
    use RefreshDatabase;
    use AuthorizeAccountContext;

    public function test_audit_event_never_duplicates_credentials_or_business_pii(): void
    {
        $user = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Audit Account', 'balance' => 0, 'owner_user_id' => $user->id]);
        Auth::login($user);

        $request = Request::create('/api/transactions', 'POST', [
            'current_pin' => '123456',
            'new_pin' => '654321',
            'password_confirmation' => 'secret-password',
            'customer_phone' => '+8801712345678',
            'receiver_account_no' => '1234567890123456',
            'receiver_name' => 'Sensitive Receiver',
            'notes' => 'private business note',
            'logo_base64' => 'data:image/png;base64,secret-image-data',
            'nested' => [
                'access_token' => 'access-secret',
                'api_secret' => 'api-secret',
                'address' => 'private address',
            ],
        ]);
        $request->attributes->set('active_account_id', $account->id);

        app(AuditLogMiddleware::class)->handle($request, fn () => response()->json(['ok' => true], 201));

        $audit = AuditLog::query()->latest('id')->firstOrFail();
        $encoded = json_encode($audit->payload, JSON_THROW_ON_ERROR);

        $this->assertSame($user->id, $audit->user_id);
        $this->assertSame($account->id, $audit->account_id);
        $this->assertSame('POST', $audit->action);
        $this->assertSame(201, $audit->payload['status_code']);
        $this->assertSame('success', $audit->payload['result']);
        $this->assertNotSame('127.0.0.1', $audit->ip_address);
        $this->assertLessThanOrEqual(45, strlen((string) $audit->ip_address));

        foreach ([
            '123456', '654321', 'secret-password', '+8801712345678',
            '1234567890123456', 'Sensitive Receiver', 'private business note',
            'secret-image-data', 'access-secret', 'api-secret', 'private address',
        ] as $secret) {
            $this->assertStringNotContainsString($secret, $encoded);
        }
    }

    public function test_audit_prune_removes_only_records_outside_configured_retention(): void
    {
        config(['safa.audit_retention_days' => 90]);
        $old = AuditLog::create([
            'action' => 'POST',
            'endpoint' => 'api/customers',
            'payload' => ['status_code' => 201],
            'created_at' => now()->subDays(91),
            'updated_at' => now()->subDays(91),
        ]);
        $current = AuditLog::create([
            'action' => 'POST',
            'endpoint' => 'api/customers',
            'payload' => ['status_code' => 201],
            'created_at' => now()->subDays(89),
            'updated_at' => now()->subDays(89),
        ]);

        $this->artisan('safa:audit-prune')->assertSuccessful();

        $this->assertDatabaseMissing('audit_logs', ['id' => $old->id]);
        $this->assertDatabaseHas('audit_logs', ['id' => $current->id]);
    }

    public function test_revoked_device_binding_cannot_be_reactivated_by_model_update(): void
    {
        $user = User::factory()->create(['is_activated' => true]);
        $binding = DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => 'REVOKED_DEVICE',
            'device_model' => 'Test',
            'fingerprint_hash' => 'FP_1',
            'is_active' => false,
            'bound_at' => now(),
        ]);

        $this->expectException(ValidationException::class);
        $binding->is_active = true;
        $binding->save();
    }

    public function test_inactive_user_cannot_create_device_binding(): void
    {
        $user = User::factory()->create(['is_activated' => false]);

        $this->expectException(ValidationException::class);
        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => 'INACTIVE_DEVICE',
            'device_model' => 'Test',
            'fingerprint_hash' => 'FP_2',
            'is_active' => true,
            'bound_at' => now(),
        ]);
    }

    public function test_share_owner_must_match_authoritative_account_owner(): void
    {
        $owner = User::factory()->create(['is_activated' => true]);
        $attacker = User::factory()->create(['is_activated' => true]);
        $target = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Victim Account', 'balance' => 0, 'owner_user_id' => $owner->id]);

        try {
            UserAccountShare::create([
                'owner_user_id' => $attacker->id,
                'shared_with_user_id' => $target->id,
                'account_id' => $account->id,
                'permissions_override' => ['can_view_customers' => true],
            ]);
            $this->fail('Forged share should be rejected.');
        } catch (ValidationException) {
            $this->assertDatabaseMissing('user_account_shares', [
                'account_id' => $account->id,
                'shared_with_user_id' => $target->id,
            ]);
        }

        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $target->id,
            'account_id' => $account->id,
            'permissions_override' => ['can_view_customers' => true],
        ]);
        $this->assertDatabaseHas('user_account_shares', [
            'owner_user_id' => $owner->id,
            'account_id' => $account->id,
            'shared_with_user_id' => $target->id,
        ]);
    }

    public function test_legacy_mismatched_share_row_cannot_authorize_account_context(): void
    {
        $owner = User::factory()->create(['is_activated' => true]);
        $forgedOwner = User::factory()->create(['is_activated' => true]);
        $target = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Protected Account', 'balance' => 0, 'owner_user_id' => $owner->id]);

        DB::table('user_account_shares')->insert([
            'owner_user_id' => $forgedOwner->id,
            'shared_with_user_id' => $target->id,
            'account_id' => $account->id,
            'permissions_override' => json_encode(['can_view_customers' => true]),
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $request = Request::create('/', 'GET', ['account_id' => $account->id]);
        $request->setUserResolver(fn () => $target);
        $context = $this->resolveAuthorizedAccountContext($request);

        $this->assertArrayHasKey('error', $context);
        $this->assertSame(403, $context['error']->getStatusCode());
    }
}
