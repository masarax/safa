<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\SystemSetting;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class WebProfileSettingsTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $role, string $mobile, string $email): User
    {
        $hash = Hash::make('123456');
        $user = User::factory()->create([
            'name' => ucfirst(str_replace('_', ' ', $role)) . ' Web Tester',
            'email' => $email,
            'mobile' => $mobile,
            'pin_hash' => $hash,
            'password' => $hash,
            'role' => $role,
            'is_activated' => true,
        ]);

        Account::create([
            'owner_user_id' => $user->id,
            'name' => $user->name . ' Business',
            'balance' => '0.00',
        ]);

        return $user;
    }

    public function test_normal_user_gets_personal_settings_and_customer_profile_without_hidden_modules(): void
    {
        $user = $this->user(User::ROLE_USER, '0536309101', 'normal-web@example.test');
        $response = $this->actingAs($user)->get('/app')->assertOk();
        $html = (string) $response->getContent();

        $this->assertStringContainsString('data-section="settings"', $html);
        $this->assertStringContainsString('id="personal-settings-form"', $html);
        $this->assertStringContainsString('id="theme-form"', $html);
        $this->assertStringContainsString('id="pin-form"', $html);
        $this->assertStringContainsString('id="customer-profile"', $html);

        $this->assertStringNotContainsString('data-section="transactions"', $html);
        $this->assertStringNotContainsString('data-panel="transactions"', $html);
        $this->assertStringNotContainsString('id="customer-transaction-form"', $html);
        $this->assertStringNotContainsString('id="config-form"', $html);
        $this->assertStringContainsString('data-suppliers-url=""', $html);
        $this->assertStringContainsString('data-transactions-url=""', $html);
        $this->assertStringContainsString('data-wallet-ledgers-url=""', $html);
        $this->assertStringContainsString('data-config-url=""', $html);
        $this->assertStringContainsString('data-users-url=""', $html);
    }

    public function test_business_user_uses_customer_and_supplier_profile_transaction_workflow(): void
    {
        $user = $this->user(User::ROLE_BUSINESS_USER, '0536309102', 'business-web@example.test');
        $response = $this->actingAs($user)->get('/app')->assertOk();
        $html = (string) $response->getContent();

        $this->assertStringContainsString('id="customer-profile"', $html);
        $this->assertStringContainsString('id="supplier-profile"', $html);
        $this->assertStringContainsString('id="customer-transaction-form"', $html);
        $this->assertStringContainsString('id="supplier-transaction-form"', $html);
        $this->assertStringContainsString('data-profile-select="suppliers"', $html);
        $this->assertStringContainsString('data-profile-select="customers"', $html);
        $this->assertStringContainsString('data-transactions-url="' . route('safa.web.transactions') . '"', $html);

        $this->assertStringNotContainsString('data-section="transactions"', $html);
        $this->assertStringNotContainsString('data-panel="transactions"', $html);
        $this->assertStringNotContainsString('Customer ID</span><input', $html);
        $this->assertStringNotContainsString('Supplier ID</span><input', $html);
        $this->assertStringNotContainsString('id="config-form"', $html);
        $this->assertStringContainsString('data-config-url=""', $html);
    }

    public function test_admin_and_superadmin_receive_only_their_system_settings_controls(): void
    {
        $admin = $this->user(User::ROLE_ADMIN, '0536309103', 'admin-web@example.test');
        $adminHtml = (string) $this->actingAs($admin)->get('/app')->assertOk()->getContent();

        foreach (['app_name', 'captain_name', 'local_currency', 'foreign_currency', 'rate_based_mode', 'supplier_rate_enabled', 'wallet_rate_enabled'] as $field) {
            $this->assertStringContainsString('name="' . $field . '"', $adminHtml);
        }
        $this->assertStringContainsString('id="settings-logo-preview"', $adminHtml);
        $this->assertStringContainsString('id="logo-form"', $adminHtml);
        $this->assertStringContainsString('User Management', $adminHtml);
        $this->assertStringNotContainsString('name="app_version"', $adminHtml);

        $super = $this->user(User::ROLE_SUPERADMIN, '0536309104', 'super-web@example.test');
        $superHtml = (string) $this->actingAs($super)->get('/app')->assertOk()->getContent();
        $this->assertStringContainsString('name="app_version"', $superHtml);
    }

    public function test_personal_language_and_pin_settings_are_available_to_normal_user(): void
    {
        $user = $this->user(User::ROLE_USER, '0536309105', 'personal-web@example.test');
        $this->actingAs($user)
            ->postJson('/app/api/settings/personal', ['language' => 'bn'])
            ->assertOk()
            ->assertJsonPath('language', 'bn')
            ->assertSessionHas('safa_web_language', 'bn');

        $this->actingAs($user)
            ->postJson('/app/api/settings/pin', [
                'current_pin' => '123456',
                'new_pin' => '654321',
                'new_pin_confirmation' => '654321',
            ])
            ->assertOk();

        $user->refresh();
        $this->assertTrue(Hash::check('654321', (string) $user->pin_hash));
        $this->assertTrue(Hash::check('654321', (string) $user->password));

        $this->actingAs($user)
            ->postJson('/app/api/settings/pin', [
                'current_pin' => '123456',
                'new_pin' => '111111',
                'new_pin_confirmation' => '111111',
            ])
            ->assertUnauthorized();
    }

    public function test_system_settings_are_admin_only_and_version_metadata_is_superadmin_only(): void
    {
        $normal = $this->user(User::ROLE_USER, '0536309106', 'no-config@example.test');
        $this->actingAs($normal)
            ->postJson('/app/api/config', ['app_name' => 'Blocked'])
            ->assertForbidden();

        $admin = $this->user(User::ROLE_ADMIN, '0536309107', 'config-admin@example.test');
        $this->actingAs($admin)
            ->postJson('/app/api/config', [
                'app_name' => 'SAFA Business',
                'captain_name' => 'Captain One',
                'local_currency' => 'bdt',
                'foreign_currency' => 'sar',
                'rate_based_mode' => true,
                'supplier_rate_enabled' => false,
                'wallet_rate_enabled' => true,
            ])
            ->assertOk()
            ->assertJsonPath('settings.app_name', 'SAFA Business')
            ->assertJsonPath('settings.captain_name', 'Captain One');

        $this->assertDatabaseHas('system_settings', [
            'app_name' => 'SAFA Business',
            'captain_name' => 'Captain One',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'supplier_rate_enabled' => 0,
        ]);

        $this->actingAs($admin)
            ->postJson('/app/api/config', ['app_version' => '9.9.9'])
            ->assertForbidden();

        $super = $this->user(User::ROLE_SUPERADMIN, '0536309108', 'config-super@example.test');
        $this->actingAs($super)
            ->postJson('/app/api/config', ['app_version' => '9.9.9'])
            ->assertOk()
            ->assertJsonPath('settings.app_version', '9.9.9');
    }

    public function test_generated_logo_uses_same_origin_path_and_legacy_absolute_logo_is_normalized(): void
    {
        $setting = SystemSetting::create([
            'app_name' => 'SAFA',
            'app_logo_url' => 'https://old.example.test/storage/logos/logo_legacy.png',
            'app_version' => '1.0.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ]);
        $this->assertSame('/storage/logos/logo_legacy.png', $setting->webLogoSource());

        $admin = $this->user(User::ROLE_ADMIN, '0536309109', 'logo-admin@example.test');
        $png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
        $response = $this->actingAs($admin)
            ->postJson('/app/api/logo', ['logo_base64' => $png])
            ->assertOk();

        $path = (string) $response->json('app_logo_path');
        $this->assertMatchesRegularExpression('#^/storage/logos/logo_[A-Za-z0-9_-]+\.png$#', $path);
        $this->assertSame($path, SystemSetting::firstOrFail()->fresh()->app_logo_url);
        $this->assertFileExists(public_path(ltrim($path, '/')));

        try {
            $html = (string) $this->actingAs($admin)->get('/app')->assertOk()->getContent();
            $this->assertStringContainsString('src="' . $path . '"', $html);
            $this->get($path)->assertOk()->assertHeader('X-Content-Type-Options', 'nosniff');
        } finally {
            @unlink(public_path(ltrim($path, '/')));
        }
    }

    public function test_web_server_rules_allow_only_generated_raster_logos_before_storage_deny(): void
    {
        foreach ([base_path('.htaccess'), public_path('.htaccess')] as $path) {
            $rules = (string) file_get_contents($path);
            $logoAllow = strpos($rules, 'storage/logos/');
            $storageDeny = strpos($rules, 'storage|tests|vendor');

            $this->assertNotFalse($logoAllow, "Missing generated-logo allow rule in {$path}");
            $this->assertNotFalse($storageDeny, "Missing storage deny rule in {$path}");
            $this->assertLessThan($storageDeny, $logoAllow, "Generated logos must be handled before storage deny in {$path}");
            $this->assertStringContainsString('logo_[A-Za-z0-9_-]+', $rules);
            $this->assertStringContainsString('png|jpe?g|gif|webp', $rules);
        }
    }
}
