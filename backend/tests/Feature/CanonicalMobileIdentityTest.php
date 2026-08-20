<?php

namespace Tests\Feature;

use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\UserManagementController;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class CanonicalMobileIdentityTest extends TestCase
{
    use RefreshDatabase;

    public function test_saudi_international_duplicate_is_rejected_before_create(): void
    {
        [$admin] = $this->manager();
        User::factory()->create(['mobile' => '0551234567', 'role' => User::ROLE_USER, 'is_activated' => true]);

        $response = app(UserManagementController::class)->store($this->request('POST', [
            'name' => 'Duplicate Saudi',
            'mobile' => '+966 55 123 4567',
            'role' => User::ROLE_USER,
            'pin' => '123456',
        ], $admin));

        $this->assertSame(422, $response->getStatusCode());
        $this->assertArrayHasKey('mobile', $response->getData(true)['errors']);
        $this->assertSame(1, User::where('mobile', '0551234567')->count());
    }

    public function test_bangladesh_international_bengali_digits_are_duplicate_on_update(): void
    {
        [$admin] = $this->manager();
        User::factory()->create(['mobile' => '01712345678', 'role' => User::ROLE_USER, 'is_activated' => true]);
        $target = User::factory()->create(['mobile' => '01812345678', 'role' => User::ROLE_USER, 'is_activated' => true]);

        $response = app(UserManagementController::class)->update($this->request('PUT', [
            'mobile' => '+৮৮০ ১৭ ১২৩৪ ৫৬৭৮',
        ], $admin), $target->id);

        $this->assertSame(422, $response->getStatusCode());
        $this->assertSame('01812345678', $target->fresh()->mobile);
    }

    public function test_arabic_indic_mobile_is_stored_in_canonical_form(): void
    {
        [$admin] = $this->manager();

        $response = app(UserManagementController::class)->store($this->request('POST', [
            'name' => 'Localized Saudi',
            'mobile' => '٠٥٥ ٩٨٧ ٦٥٤٣',
            'role' => User::ROLE_USER,
            'pin' => '123456',
        ], $admin));

        $this->assertSame(201, $response->getStatusCode());
        $this->assertSame('0559876543', $response->getData(true)['user']['mobile']);
        $this->assertDatabaseHas('users', ['mobile' => '0559876543']);
    }

    public function test_unlinked_compatibility_operator_blocks_equivalent_mobile(): void
    {
        [$admin] = $this->manager();
        DB::table('operator_accounts')->insert([
            'user_id' => null,
            'name' => 'Legacy Operator',
            'email' => null,
            'mobile' => '+966 54 111 2233',
            'role' => User::ROLE_USER,
            'pin_hash' => null,
            'is_activated' => true,
            'permissions' => null,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $response = app(UserManagementController::class)->store($this->request('POST', [
            'name' => 'Conflicting User',
            'mobile' => '0541112233',
            'role' => User::ROLE_USER,
            'pin' => '123456',
        ], $admin));

        $this->assertSame(422, $response->getStatusCode());
        $this->assertDatabaseMissing('users', ['mobile' => '0541112233']);
    }

    public function test_share_lookup_accepts_formatted_international_mobile(): void
    {
        [$owner, $account] = $this->manager();
        $target = User::factory()->create([
            'mobile' => '01776543210',
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);

        $response = app(AccountContextController::class)->share($this->request('POST', [
            'account_id' => $account->id,
            'mobile' => '+৮৮০ ১৭ ৭৬৫৪ ৩২১০',
        ], $owner));

        $this->assertSame(200, $response->getStatusCode());
        $this->assertDatabaseHas('user_account_shares', [
            'account_id' => $account->id,
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $target->id,
        ]);
    }

    private function manager(): array
    {
        $admin = User::factory()->create([
            'mobile' => '0500000001',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Canonical Identity',
            'balance' => 0,
            'owner_user_id' => $admin->id,
        ]);

        return [$admin, $account];
    }

    private function request(string $method, array $payload, User $user): Request
    {
        $request = Request::create('/api/identity', $method, $payload);
        $request->setUserResolver(fn () => $user);
        return $request;
    }
}
