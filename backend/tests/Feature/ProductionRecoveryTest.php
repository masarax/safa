<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\Permission;
use App\Models\Role;
use App\Models\SystemSetting;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class ProductionRecoveryTest extends TestCase
{
    use RefreshDatabase;

    public function test_project_root_product_assets_are_served_with_browser_content_types(): void
    {
        $this->get('/safa-web.css')
            ->assertOk()
            ->assertHeader('Content-Type', 'text/css; charset=utf-8');

        $this->get('/safa-web-product.css')
            ->assertOk()
            ->assertHeader('Content-Type', 'text/css; charset=utf-8')
            ->assertSee('--brand-green', false);

        $this->get('/safa-web-product.js')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/javascript; charset=utf-8');
    }

    public function test_initial_index_bootstrap_rejects_an_invalid_setup_secret(): void
    {
        Config::set('safa.setup_token', 'correct-recovery-secret');

        $this->post('/index/bootstrap', [
            'setup_secret' => 'wrong-secret',
            'name' => 'Production Admin',
            'mobile' => '01700000000',
            'email' => 'admin@example.test',
            'pin' => '654321',
            'pin_confirmation' => '654321',
        ])->assertSessionHasErrors('setup_secret');

        $this->assertDatabaseCount('users', 0);
    }

    public function test_initial_index_bootstrap_creates_login_capable_superadmin_and_full_reference_seed(): void
    {
        Config::set('safa.setup_token', 'correct-recovery-secret');

        $response = $this->post('/index/bootstrap', [
            'setup_secret' => 'correct-recovery-secret',
            'name' => 'Production Admin',
            'mobile' => '+880 1700-000000',
            'email' => 'admin@example.test',
            'pin' => '654321',
            'pin_confirmation' => '654321',
        ]);

        $response->assertRedirect(route('safa.app'));

        $admin = User::query()->where('email', 'admin@example.test')->firstOrFail();
        $this->assertTrue($admin->isSuperAdmin());
        $this->assertTrue((bool) $admin->is_activated);
        $this->assertSame('8801700000000', $admin->mobile);
        $this->assertTrue(Hash::check('654321', (string) $admin->pin_hash));

        $account = Account::query()->firstOrFail();
        $this->assertSame($admin->id, (int) $account->owner_user_id);
        $this->assertSame(4, Role::query()->count());
        $this->assertSame(count(User::defaultPermissions(false)), Permission::query()->count());
        $this->assertNotNull(SystemSetting::query()->first());
        $this->assertNotNull(AppVersion::query()->where('platform', 'android')->first());

        $this->assertAuthenticatedAs($admin);
    }

    public function test_bootstrap_is_closed_to_guests_after_superadmin_exists(): void
    {
        User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->get('/index')->assertRedirect(route('safa.login'));
        $this->post('/index/bootstrap', [])->assertForbidden();
    }

    public function test_safe_seed_refresh_is_idempotent_and_preserves_existing_business_rows(): void
    {
        $admin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::query()->create([
            'owner_user_id' => $admin->id,
            'name' => 'Existing Business',
            'balance' => '125.50',
        ]);

        $this->actingAs($admin)->post('/index/seed')->assertRedirect(route('safa.setup'));
        $this->actingAs($admin)->post('/index/seed')->assertRedirect(route('safa.setup'));

        $account->refresh();
        $this->assertSame('Existing Business', $account->name);
        $this->assertSame('125.50', (string) $account->balance);
        $this->assertSame(4, Role::query()->count());
        $this->assertSame(count(User::defaultPermissions(false)), Permission::query()->count());
    }
}
