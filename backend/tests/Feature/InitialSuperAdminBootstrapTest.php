<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\User;
use App\Support\InitialSuperAdminBootstrap;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class InitialSuperAdminBootstrapTest extends TestCase
{
    use RefreshDatabase;

    private const PENDING_MIGRATION = '2026_08_10_000002_add_address_to_customers';
    private const MAINTENANCE_TOKEN = 'test-maintenance-token';

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.maintenance_token', self::MAINTENANCE_TOKEN);
    }

    public function test_zero_privileged_user_installation_shows_only_superadmin_bootstrap(): void
    {
        $this->get('/install')
            ->assertOk()
            ->assertSee('Create the first Super Admin')
            ->assertSee('Create Super Admin')
            ->assertSee('Maintenance key')
            ->assertDontSee('Database Host')
            ->assertDontSee('Database Password')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed');
    }

    public function test_bootstrap_write_requires_the_server_maintenance_key(): void
    {
        $this->post('/install/super-admin', $this->payload([
            'maintenance_token' => 'wrong-token',
        ]))->assertForbidden();

        $this->assertDatabaseMissing('users', ['role' => User::ROLE_SUPERADMIN]);
    }

    public function test_valid_bootstrap_creates_one_login_capable_superadmin_and_then_closes_installation(): void
    {
        $this->post('/install/super-admin', $this->payload())
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $admin = User::query()->where('email', 'bootstrap@example.test')->firstOrFail();
        $this->assertTrue($admin->isSuperAdmin());
        $this->assertTrue((bool) $admin->is_activated);
        $this->assertSame('01712345678', $admin->mobile);
        $this->assertTrue(Hash::check('654321', (string) $admin->pin_hash));
        $this->assertTrue(Hash::check('654321', (string) $admin->password));

        $account = Account::query()->firstOrFail();
        $this->assertSame($admin->id, (int) $account->owner_user_id);
        $this->assertSame(1, User::query()->where('role', User::ROLE_SUPERADMIN)->count());

        $this->get('/install')->assertNotFound();
        $this->post('/install/super-admin', $this->payload())->assertNotFound();
    }

    public function test_any_existing_admin_closes_bootstrap_even_when_inactive(): void
    {
        User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => false,
        ]);

        $this->get('/install')->assertNotFound();
        $this->post('/install/super-admin', $this->payload())->assertNotFound();
    }

    public function test_pending_migration_redirects_bootstrap_to_database_recovery(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();
        $this->assertSame(1, $deleted);

        $this->get('/install')->assertRedirect(route('system.update.show'));
        $this->post('/install/super-admin', $this->payload())
            ->assertRedirect(route('system.update.show'));
        $this->assertDatabaseMissing('users', ['email' => 'bootstrap@example.test']);
    }

    public function test_bootstrap_preserves_existing_business_data(): void
    {
        $existingUser = User::factory()->create([
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $account = Account::query()->create([
            'owner_user_id' => $existingUser->id,
            'name' => 'Existing Business',
            'balance' => '725.50',
        ]);
        DB::table('customers')->insert([
            'account_id' => $account->id,
            'local_id' => 9001,
            'name' => 'Existing Customer',
            'phone' => '01700000000',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->post('/install/super-admin', $this->payload())
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertSame(1, Account::query()->count());
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'owner_user_id' => $existingUser->id,
            'name' => 'Existing Business',
            'balance' => '725.50',
        ]);
        $this->assertDatabaseHas('customers', [
            'account_id' => $account->id,
            'local_id' => 9001,
            'name' => 'Existing Customer',
        ]);
        $this->assertDatabaseHas('users', [
            'email' => 'bootstrap@example.test',
            'role' => User::ROLE_SUPERADMIN,
        ]);
    }

    public function test_locked_bootstrap_cannot_start_a_second_setup_write(): void
    {
        $lock = Cache::lock(InitialSuperAdminBootstrap::LOCK_KEY, 15);
        $this->assertTrue($lock->get());

        try {
            $this->from('/install')
                ->post('/install/super-admin', $this->payload())
                ->assertRedirect('/install')
                ->assertSessionHas('error');
        } finally {
            $lock->release();
        }

        $this->assertDatabaseMissing('users', ['email' => 'bootstrap@example.test']);
    }

    public function test_validation_never_flashes_pin_or_maintenance_key(): void
    {
        $this->from('/install')->post('/install/super-admin', $this->payload([
            'pin_confirmation' => '111111',
        ]))->assertRedirect('/install');

        $this->assertSame('Bootstrap Admin', session()->getOldInput('name'));
        $this->assertNull(session()->getOldInput('pin'));
        $this->assertNull(session()->getOldInput('pin_confirmation'));
        $this->assertNull(session()->getOldInput('maintenance_token'));
        $this->assertDatabaseMissing('users', ['email' => 'bootstrap@example.test']);
    }

    public function test_missing_server_maintenance_authorization_disables_the_bootstrap_submit_control(): void
    {
        Config::set('safa.maintenance_token', '');

        $this->get('/install')
            ->assertOk()
            ->assertSee('Server authorization required')
            ->assertSee('type="submit" disabled', false);

        $this->post('/install/super-admin', $this->payload([
            'maintenance_token' => '',
        ]))->assertForbidden();
    }

    private function payload(array $overrides = []): array
    {
        return array_merge([
            'name' => 'Bootstrap Admin',
            'mobile' => '+880 1712-345678',
            'email' => 'Bootstrap@Example.Test',
            'pin' => '654321',
            'pin_confirmation' => '654321',
            'maintenance_token' => self::MAINTENANCE_TOKEN,
            'language' => 'en',
        ], $overrides);
    }
}
