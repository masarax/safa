<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class FourTierRolePresetTest extends TestCase
{
    use RefreshDatabase;

    public function test_role_aliases_normalize_without_privilege_elevation(): void
    {
        $this->assertSame(User::ROLE_SUPERADMIN, User::normalizeRole('SuperAdmin'));
        $this->assertSame(User::ROLE_ADMIN, User::normalizeRole('admin'));
        $this->assertSame(User::ROLE_BUSINESS_USER, User::normalizeRole('business_user'));
        $this->assertSame(User::ROLE_BUSINESS_USER, User::normalizeRole('manager'));
        $this->assertSame(User::ROLE_USER, User::normalizeRole('staff'));
        $this->assertSame(User::ROLE_USER, User::normalizeRole('unknown-future-role'));
    }

    public function test_normal_user_has_only_customer_and_daily_income_expense_business_access(): void
    {
        $permissions = User::permissionsForRole(User::ROLE_USER);

        $this->assertTrue($permissions['can_view_customers']);
        $this->assertTrue($permissions['can_add_customers']);
        $this->assertTrue($permissions['can_edit_customers']);
        $this->assertTrue($permissions['can_delete_customers']);
        $this->assertTrue($permissions['can_manage_expenses']);
        $this->assertFalse($permissions['can_view_suppliers']);
        $this->assertFalse($permissions['can_view_transactions']);
        $this->assertFalse($permissions['can_manage_wallet']);
        $this->assertFalse($permissions['can_view_reports']);
    }

    public function test_business_user_adds_supplier_and_related_transaction_access_without_admin_wallet_access(): void
    {
        $permissions = User::permissionsForRole(User::ROLE_BUSINESS_USER);

        $this->assertTrue($permissions['can_view_customers']);
        $this->assertTrue($permissions['can_manage_expenses']);
        $this->assertTrue($permissions['can_view_suppliers']);
        $this->assertTrue($permissions['can_add_suppliers']);
        $this->assertTrue($permissions['can_view_transactions']);
        $this->assertTrue($permissions['can_add_transactions']);
        $this->assertFalse($permissions['can_manage_wallet']);
        $this->assertFalse($permissions['can_view_reports']);
    }

    public function test_admin_and_superadmin_have_full_business_permissions(): void
    {
        foreach ([User::ROLE_ADMIN, User::ROLE_SUPERADMIN] as $role) {
            foreach (User::permissionsForRole($role) as $allowed) {
                $this->assertTrue($allowed, "Expected every business permission to be enabled for {$role}");
            }
        }
    }

    public function test_admin_cannot_manage_admin_or_superadmin_but_superadmin_can_manage_admin(): void
    {
        $admin = new User(['role' => User::ROLE_ADMIN]);
        $super = new User(['role' => User::ROLE_SUPERADMIN]);

        $this->assertTrue($admin->canManageRole(User::ROLE_USER));
        $this->assertTrue($admin->canManageRole(User::ROLE_BUSINESS_USER));
        $this->assertFalse($admin->canManageRole(User::ROLE_ADMIN));
        $this->assertFalse($admin->canManageRole(User::ROLE_SUPERADMIN));
        $this->assertTrue($super->canManageRole(User::ROLE_ADMIN));
        $this->assertFalse($super->canManageRole(User::ROLE_SUPERADMIN));
    }

    public function test_persisted_permission_payload_cannot_override_role_preset(): void
    {
        $hash = Hash::make('123456');
        $user = User::factory()->create([
            'mobile' => '0536308999',
            'pin_hash' => $hash,
            'password' => $hash,
            'role' => User::ROLE_USER,
            'is_activated' => true,
            'permissions' => ['can_manage_wallet' => true, 'can_view_suppliers' => true],
        ]);

        $this->assertFalse($user->fresh()->getFormattedPermissions()['can_manage_wallet']);
        $this->assertFalse($user->fresh()->getFormattedPermissions()['can_view_suppliers']);
    }
}
