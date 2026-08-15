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
        Account::create(['owner_user_id' => $user->id, 'name' => $user->name . ' Business', 'balance' => '0.00']);
        return $user;
    }

    public function test_normal_user_gets_personal_settings_without_hidden_admin_modules(): void
    {
        $user = $this->user(User::ROLE_USER, '0536309101', 'normal-web@example.test');
        $html = (string) $this->actingAs($user)->get('/app')->assertOk()->getContent();

        $this->assertStringContainsString('data-open-settings', $html);
        $this->assertStringContainsString('id="profile-settings-form"', $html);
        $this->assertStringContainsString('id="personal-settings-form"', $html);
        $this->assertStringContainsString('id="appearance-select"', $html);
        $this->assertStringContainsString('id="pin-form"', $html);
        $this->assertStringContainsString('data-nav="customers"', $html);
        $this->assertStringNotContainsString('data-nav="suppliers"', $html);
        $this->assertStringNotContainsString('data-nav="wallet"', $html);
        $this->assertStringNotContainsString('id="config-form"', $html);
        $this->assertStringNotContainsString('id="user-management-settings"', $html);
        $this->assertStringContainsString('data-suppliers-url=""', $html);
        $this->assertStringContainsString('data-config-url=""', $html);
        $this->assertStringContainsString('data-users-url=""', $html);
    }

    public function test_business_user_gets_android_style_customer_and_supplier_modules_without_wallet_management(): void
    {
        $user = $this->user(User::ROLE_BUSINESS_USER, '0536309102', 'business-web@example.test');
        $html = (string) $this->actingAs($user)->get('/app')->assertOk()->getContent();

        $this->assertStringContainsString('data-nav="customers"', $html);
        $this->assertStringContainsString('data-nav="suppliers"', $html);
        $this->assertStringNotContainsString('data-nav="transactions"', $html);
        $this->assertStringNotContainsString('data-nav="wallet"', $html);
        $this->assertStringContainsString('data-customer-sale-url="' . route('safa.web.mobile.customer-sale') . '"', $html);
        $this->assertStringContainsString('data-mobile-transactions-url="' . url('/app/api/mobile/transactions') . '"', $html);
        $this->assertStringNotContainsString('id="config-form"', $html);
        $this->assertStringContainsString('data-config-url=""', $html);
    }

    public function test_admin_and_superadmin_receive_system_settings_without_user_identity_in_brand_form(): void
    {
        $admin = $this->user(User::ROLE_ADMIN, '0536309103', 'admin-web@example.test');
        $adminHtml = (string) $this->actingAs($admin)->get('/app')->assertOk()->getContent();

        foreach (['app_name', 'local_currency', 'foreign_currency', 'rate_based_mode', 'supplier_rate_enabled', 'wallet_rate_enabled'] as $field) {
            $this->assertStringContainsString('name="' . $field . '"', $adminHtml);
        }
        $this->assertStringContainsString('id="profile-settings-form"', $adminHtml);
        $this->assertStringContainsString('id="brand-business-config"', $adminHtml);
        $this->assertStringContainsString('id="settings-logo-preview"', $adminHtml);
        $this->assertStringContainsString('id="logo-form"', $adminHtml);
        $this->assertStringContainsString('User Management', $adminHtml);
        $this->assertStringNotContainsString('name="captain_name"', $adminHtml);
        $this->assertStringNotContainsString('name="app_version"', $adminHtml);

        preg_match('/<article[^>]+id="brand-business-config".*?<\/article>/s', $adminHtml, $brand);
        $this->assertNotEmpty($brand);
        $this->assertStringNotContainsString('name="name"', $brand[0]);
        $this->assertStringNotContainsString('name="mobile"', $brand[0]);

        $super = $this->user(User::ROLE_SUPERADMIN, '0536309104', 'super-web@example.test');
        $superHtml = (string) $this->actingAs($super)->get('/app')->assertOk()->getContent();
        $this->assertStringContainsString('name="app_version"', $superHtml);
    }

    public function test_personal_language_profile_and_pin_settings_are_available_to_normal_user(): void
    {
        $user = $this->user(User::ROLE_USER, '0536309105', 'personal-web@example.test');
        $this->actingAs($user)->postJson('/app/api/settings/personal', ['language' => 'bn'])
            ->assertOk()->assertJsonPath('language', 'bn')->assertSessionHas('safa_web_language', 'bn');

        $this->actingAs($user)->postJson('/app/api/settings/profile', ['name' => 'Personal Updated', 'mobile' => '0536309195'])
            ->assertOk()->assertJsonPath('user.name', 'Personal Updated')->assertJsonPath('user.mobile', '0536309195');
        $this->assertDatabaseHas('users', ['id' => $user->id, 'name' => 'Personal Updated', 'mobile' => '0536309195']);

        $this->actingAs($user)->postJson('/app/api/settings/pin', [
            'current_pin' => '123456', 'new_pin' => '654321', 'new_pin_confirmation' => '654321',
        ])->assertOk();
        $user->refresh();
        $this->assertTrue(Hash::check('654321', (string) $user->pin_hash));
        $this->assertTrue(Hash::check('654321', (string) $user->password));

        $this->actingAs($user)->postJson('/app/api/settings/pin', [
            'current_pin' => '123456', 'new_pin' => '111111', 'new_pin_confirmation' => '111111',
        ])->assertUnauthorized();
    }

    public function test_system_settings_are_admin_only_version_is_superadmin_only_and_captain_name_is_not_writable(): void
    {
        $normal = $this->user(User::ROLE_USER, '0536309106', 'no-config@example.test');
        $this->actingAs($normal)->postJson('/app/api/config', ['app_name' => 'Blocked'])->assertForbidden();

        $admin = $this->user(User::ROLE_ADMIN, '0536309107', 'config-admin@example.test');
        $originalName = $admin->name;
        $this->actingAs($admin)->postJson('/app/api/config', [
            'app_name' => 'SAFA Business', 'local_currency' => 'bdt', 'foreign_currency' => 'sar',
            'rate_based_mode' => true, 'supplier_rate_enabled' => false, 'wallet_rate_enabled' => true,
        ])->assertOk()->assertJsonPath('settings.app_name', 'SAFA Business');

        $this->assertDatabaseHas('system_settings', [
            'app_name' => 'SAFA Business', 'local_currency' => 'BDT', 'foreign_currency' => 'SAR', 'supplier_rate_enabled' => 0,
        ]);
        $this->assertSame($originalName, $admin->fresh()->name);

        $this->actingAs($admin)->postJson('/app/api/config', ['captain_name' => 'Must Not Change Identity'])->assertUnprocessable();
        $this->assertSame($originalName, $admin->fresh()->name);
        $this->actingAs($admin)->postJson('/app/api/config', ['app_version' => '9.9.9'])->assertForbidden();

        $super = $this->user(User::ROLE_SUPERADMIN, '0536309108', 'config-super@example.test');
        $this->actingAs($super)->postJson('/app/api/config', ['app_version' => '9.9.9'])
            ->assertOk()->assertJsonPath('settings.app_version', '9.9.9');
    }

    public function test_generated_logo_uses_same_origin_path_and_legacy_absolute_logo_is_normalized(): void
    {
        $setting = SystemSetting::create([
            'app_name' => 'SAFA', 'app_logo_url' => 'https://old.example.test/storage/logos/logo_legacy.png', 'app_version' => '1.0.0',
            'local_currency' => 'BDT', 'foreign_currency' => 'SAR', 'rate_based_mode' => true, 'supplier_rate_enabled' => true, 'wallet_rate_enabled' => true,
        ]);
        $this->assertSame('/storage/logos/logo_legacy.png', $setting->webLogoSource());

        $admin = $this->user(User::ROLE_ADMIN, '0536309109', 'logo-admin@example.test');
        $png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
        $response = $this->actingAs($admin)->postJson('/app/api/logo', ['logo_base64' => $png])->assertOk();
        $path = (string) $response->json('app_logo_path');
        $this->assertMatchesRegularExpression('#^/storage/logos/logo_[A-Za-z0-9_-]+\.png$#', $path);
        $this->assertSame($path, SystemSetting::firstOrFail()->fresh()->app_logo_url);
        $this->assertFileExists(public_path(ltrim($path, '/')));
        try {
            $html = (string) $this->actingAs($admin)->get('/app')->assertOk()->getContent();
            $this->assertStringContainsString('src="' . $path . '"', $html);
            $this->get($path)->assertOk()->assertHeader('X-Content-Type-Options', 'nosniff');
        } finally { @unlink(public_path(ltrim($path, '/'))); }
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
