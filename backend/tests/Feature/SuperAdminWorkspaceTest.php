<?php

namespace Tests\Feature;

use App\Http\Controllers\AccountContextController;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use App\Support\BusinessPermissions;
use Database\Seeders\SuperAdminWorkspaceSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class SuperAdminWorkspaceTest extends TestCase
{
    use RefreshDatabase;

    public function test_seed_repairs_superadmin_permissions_and_creates_one_default_workspace_idempotently(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        DB::table('users')->where('id', $superAdmin->id)->update([
            'permissions' => json_encode(User::defaultPermissions(false), JSON_THROW_ON_ERROR),
        ]);

        $this->seed(SuperAdminWorkspaceSeeder::class);
        $this->seed(SuperAdminWorkspaceSeeder::class);

        $superAdmin->refresh();
        foreach (User::permissionsForRole(User::ROLE_SUPERADMIN) as $permission => $allowed) {
            $this->assertTrue($allowed, $permission . ' must belong to the SuperAdmin preset.');
            $this->assertTrue((bool) ($superAdmin->permissions[$permission] ?? false), $permission . ' must be repaired by the seeder.');
        }

        $this->assertSame(1, Account::query()->count());
        $account = Account::query()->firstOrFail();
        $this->assertSame((int) $superAdmin->id, (int) $account->owner_user_id);
        $this->assertSame('SAFA Account', $account->name);
    }

    public function test_seed_does_not_create_identity_or_workspace_without_an_existing_superadmin(): void
    {
        $this->seed(SuperAdminWorkspaceSeeder::class);

        $this->assertSame(0, User::query()->count());
        $this->assertSame(0, Account::query()->count());
    }

    public function test_superadmin_only_sees_owned_and_explicitly_shared_business_accounts(): void
    {
        $superAdmin = User::factory()->create([
            'name' => 'Root Admin',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $ownerA = User::factory()->create(['role' => User::ROLE_BUSINESS_USER, 'is_activated' => true]);
        $ownerB = User::factory()->create(['role' => User::ROLE_BUSINESS_USER, 'is_activated' => true]);
        $rootAccount = Account::create(['name' => 'Root Workspace', 'balance' => 0, 'owner_user_id' => $superAdmin->id]);
        Account::create(['name' => 'Alpha', 'balance' => 0, 'owner_user_id' => $ownerA->id]);
        $accountB = Account::create(['name' => 'Beta', 'balance' => 0, 'owner_user_id' => $ownerB->id]);

        $controller = app(AccountContextController::class);
        $request = Request::create('/api/accounts', 'GET');
        $request->setUserResolver(fn () => $superAdmin);
        $payload = $controller->index($request)->getData(true);

        $this->assertSame($rootAccount->id, $payload['active_account_id']);
        $this->assertSame($rootAccount->id, $payload['owned_account_id']);
        $this->assertSame([$rootAccount->id], array_column($payload['accounts'], 'account_id'));

        $unauthorizedSwitch = Request::create('/api/accounts/switch', 'POST', ['account_id' => $accountB->id]);
        $unauthorizedSwitch->setUserResolver(fn () => $superAdmin);
        $this->assertSame(403, $controller->switch($unauthorizedSwitch)->getStatusCode());

        UserAccountShare::create([
            'owner_user_id' => $ownerB->id,
            'shared_with_user_id' => $superAdmin->id,
            'account_id' => $accountB->id,
            'permissions_override' => ['can_view_customers' => true],
        ]);

        $sharedRequest = Request::create('/api/accounts', 'GET');
        $sharedRequest->setUserResolver(fn () => $superAdmin);
        $sharedPayload = $controller->index($sharedRequest)->getData(true);
        $this->assertSame(
            [$rootAccount->id, $accountB->id],
            array_column($sharedPayload['accounts'], 'account_id')
        );
        $this->assertSame(['OWNER', 'MEMBER'], array_column($sharedPayload['accounts'], 'role'));

        $authorizedSwitch = Request::create('/api/accounts/switch', 'POST', ['account_id' => $accountB->id]);
        $authorizedSwitch->setUserResolver(fn () => $superAdmin);
        $switchPayload = $controller->switch($authorizedSwitch)->getData(true);
        $this->assertSame($accountB->id, $switchPayload['active_account_id']);
    }

    public function test_only_real_owner_can_share_and_recipient_keeps_own_default_account(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $owner = User::factory()->create([
            'role' => User::ROLE_BUSINESS_USER,
            'is_activated' => true,
        ]);
        $recipient = User::factory()->create([
            'mobile' => '966500000001',
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Owner Workspace',
            'balance' => 0,
            'owner_user_id' => $owner->id,
        ]);

        $controller = app(AccountContextController::class);
        $forbiddenShare = Request::create('/api/accounts/share', 'POST', [
            'account_id' => $account->id,
            'mobile' => $recipient->mobile,
        ]);
        $forbiddenShare->setUserResolver(fn () => $superAdmin);
        $this->assertSame(403, $controller->share($forbiddenShare)->getStatusCode());

        $ownerShare = Request::create('/api/accounts/share', 'POST', [
            'account_id' => $account->id,
            'mobile' => $recipient->mobile,
            'permissions_override' => ['can_view_customers' => true],
        ]);
        $ownerShare->setUserResolver(fn () => $owner);
        $this->assertSame(200, $controller->share($ownerShare)->getStatusCode());

        $share = UserAccountShare::query()
            ->where('account_id', $account->id)
            ->where('shared_with_user_id', $recipient->id)
            ->firstOrFail();
        $this->assertSame((int) $owner->id, (int) $share->owner_user_id);

        $recipientRequest = Request::create('/api/accounts', 'GET');
        $recipientRequest->setUserResolver(fn () => $recipient);
        $recipientPayload = $controller->index($recipientRequest)->getData(true);
        $recipientOwnedId = (int) $recipientPayload['owned_account_id'];

        $this->assertNotSame((int) $account->id, $recipientOwnedId);
        $this->assertSame($recipientOwnedId, (int) $recipientPayload['active_account_id']);
        $this->assertSame(
            [$recipientOwnedId, (int) $account->id],
            array_map('intval', array_column($recipientPayload['accounts'], 'account_id'))
        );
    }

    public function test_shared_member_cannot_delegate_account_to_another_user(): void
    {
        $owner = User::factory()->create([
            'role' => User::ROLE_BUSINESS_USER,
            'is_activated' => true,
        ]);
        $member = User::factory()->create([
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $recipient = User::factory()->create([
            'mobile' => '966500000002',
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Private Workspace',
            'balance' => 0,
            'owner_user_id' => $owner->id,
        ]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $member->id,
            'account_id' => $account->id,
            'permissions_override' => null,
        ]);

        $request = Request::create('/api/accounts/share', 'POST', [
            'account_id' => $account->id,
            'mobile' => $recipient->mobile,
        ]);
        $request->setUserResolver(fn () => $member);
        $response = app(AccountContextController::class)->share($request);

        $this->assertSame(403, $response->getStatusCode());
        $this->assertFalse(UserAccountShare::query()->where('account_id', $account->id)->where('shared_with_user_id', $recipient->id)->exists());
    }

    public function test_shared_read_denial_also_disables_mutations_and_applies_to_superadmin_recipient(): void
    {
        $owner = User::factory()->create([
            'role' => User::ROLE_BUSINESS_USER,
            'is_activated' => true,
        ]);
        $recipient = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);
        $account = Account::create([
            'name' => 'Restricted Shared Workspace',
            'balance' => 0,
            'owner_user_id' => $owner->id,
        ]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $recipient->id,
            'account_id' => $account->id,
            'permissions_override' => ['can_view_customers' => false],
        ]);

        $permissions = BusinessPermissions::effective($recipient, $account->id);
        $this->assertFalse($permissions['can_view_customers']);
        $this->assertFalse($permissions['can_add_customers']);
        $this->assertFalse($permissions['can_edit_customers']);
        $this->assertFalse($permissions['can_delete_customers']);
    }

    public function test_unshared_accounts_remain_invisible_to_lower_roles(): void
    {
        $user = User::factory()->create([
            'role' => User::ROLE_BUSINESS_USER,
            'is_activated' => true,
        ]);
        $other = User::factory()->create([
            'role' => User::ROLE_BUSINESS_USER,
            'is_activated' => true,
        ]);
        $owned = Account::create(['name' => 'Mine', 'balance' => 0, 'owner_user_id' => $user->id]);
        Account::create(['name' => 'Other', 'balance' => 0, 'owner_user_id' => $other->id]);

        $request = Request::create('/api/accounts', 'GET');
        $request->setUserResolver(fn () => $user);
        $payload = app(AccountContextController::class)->index($request)->getData(true);

        $this->assertSame($owned->id, $payload['active_account_id']);
        $this->assertCount(1, $payload['accounts']);
        $this->assertSame($owned->id, $payload['accounts'][0]['account_id']);
        $this->assertSame('OWNER', $payload['accounts'][0]['role']);
    }
}
