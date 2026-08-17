<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Permission;
use App\Models\Role;
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
            ->assertHeader('Content-Type', 'text/css; charset=utf-8');

        $this->assertStringContainsString(
            '--brand-green',
            (string) file_get_contents(public_path('safa-web-product.css'))
        );

        $this->get('/safa-web-product.js')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/javascript; charset=utf-8');
    }

    public function test_index_setup_surface_is_completely_removed(): void
    {
        $this->get('/index')->assertNotFound();
        $this->post('/index/bootstrap')->assertStatus(405);
        $this->post('/index/seed')->assertStatus(405);

        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $this->assertStringNotContainsString('SetupController', $routes);
        $this->assertStringNotContainsString("Route::get('/index'", $routes);
        $this->assertStringNotContainsString("Route::post('/index", $routes);
    }

    public function test_login_is_minimal_and_rejected_explanatory_copy_is_absent(): void
    {
        $this->get('/login?lang=en')
            ->assertOk()
            ->assertSee('Sign in')
            ->assertSee('Mobile number or email')
            ->assertSee('PIN / password')
            ->assertDontSee('Use your mobile number or email with your existing PIN/password.')
            ->assertDontSee('Your browser session is protected with HttpOnly cookies and CSRF protection.')
            ->assertDontSee('Secure business management');

        $this->get('/login?lang=bn')
            ->assertOk()
            ->assertDontSee('আপনার মোবাইল নম্বর অথবা ইমেইল এবং বিদ্যমান পিন/পাসওয়ার্ড ব্যবহার করুন।')
            ->assertDontSee('আপনার ব্রাউজার সেশন HttpOnly কুকি ও CSRF সুরক্ষার মাধ্যমে নিরাপদ রাখা হয়।');
    }

    public function test_legacy_web_recovery_seed_is_disabled_without_env_backed_admin_credentials(): void
    {
        $this->get('/system/update')
            ->assertOk()
            ->assertSee('Run Seed')
            ->assertSeeHtml('data-seed-state="admin-config-required"')
            ->assertSeeHtml('data-maintenance-action="seed" type="submit" disabled');
    }

    public function test_legacy_web_recovery_seed_cannot_create_an_administrator(): void
    {
        Config::set('safa.maintenance_token', 'recovery-maintenance-secret');

        $this->from('/system/update')->post('/system/update/seed', [
            'maintenance_token' => 'recovery-maintenance-secret',
        ])
            ->assertRedirect('/system/update')
            ->assertSessionHas('error');

        $this->assertFalse(User::query()->where('role', User::ROLE_SUPERADMIN)->exists());
    }

    public function test_seed_is_idempotent_preserves_business_data_and_never_resets_existing_admin_pin(): void
    {
        $admin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
            'pin_hash' => Hash::make('111111'),
            'password' => Hash::make('111111'),
        ]);
        $originalPinHash = $admin->pin_hash;
        $account = Account::query()->create([
            'owner_user_id' => $admin->id,
            'name' => 'Existing Business',
            'balance' => '125.50',
        ]);

        $this->actingAs($admin)->post('/system/update/seed')->assertRedirect(route('system.update.show'));
        $this->actingAs($admin)->post('/system/update/seed')->assertRedirect(route('system.update.show'));

        $account->refresh();
        $admin->refresh();
        $this->assertSame('Existing Business', $account->name);
        $this->assertSame('125.50', (string) $account->balance);
        $this->assertSame($originalPinHash, $admin->pin_hash);
        $this->assertSame(4, Role::query()->count());
        $this->assertSame(count(User::defaultPermissions(false)), Permission::query()->count());
    }
}
