<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class WebApplicationSecurityTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $role, string $mobile, string $email): User
    {
        $hash = Hash::make('123456');

        return User::factory()->create([
            'name' => ucfirst(str_replace('_', ' ', $role)) . ' Tester',
            'email' => $email,
            'mobile' => $mobile,
            'pin_hash' => $hash,
            'password' => $hash,
            'role' => $role,
            'is_activated' => true,
        ]);
    }

    public function test_login_page_has_strict_security_headers_and_csrf_form(): void
    {
        $response = $this->get('/login');
        $response->assertOk()->assertSee('SAFA')->assertSee('name="_token"', false);

        $csp = (string) $response->headers->get('Content-Security-Policy');
        $this->assertStringContainsString("script-src 'self'", $csp);
        $this->assertStringNotContainsString("'unsafe-eval'", $csp);
        $this->assertStringNotContainsString("script-src 'self' 'unsafe-inline'", $csp);
        $response->assertHeader('X-Content-Type-Options', 'nosniff');
        $response->assertHeader('X-Frame-Options', 'SAMEORIGIN');
    }

    public function test_unauthenticated_browser_cannot_access_application_data(): void
    {
        $this->get('/app')->assertRedirect('/login');
        $this->getJson('/app/api/customers')->assertUnauthorized();
    }

    public function test_front_controllers_allow_browser_app_routes_without_exposing_app_source(): void
    {
        foreach ([base_path('index.php'), public_path('index.php')] as $path) {
            $source = (string) file_get_contents($path);

            $this->assertStringContainsString("#^/app(?:/?|/api(?:/.*)?)$#i", $source);
            $this->assertStringContainsString("preg_match('/(^|\\/)app(\\/|$)/i', \$parsedPath)", $source);
            $this->assertStringNotContainsString("(app|bootstrap|config|database", $source);
            $this->assertStringContainsString("(bootstrap|config|database", $source);
        }
    }

    public function test_user_can_login_by_email_and_session_is_regenerated(): void
    {
        $user = $this->user(User::ROLE_USER, '0536308965', 'normal@example.test');

        $this->post('/login', [
            'identity' => strtoupper($user->email),
            'credential' => '123456',
            'language' => 'en',
        ])->assertRedirect(route('safa.app'));

        $this->assertAuthenticatedAs($user);
        $this->get('/app')->assertOk()->assertSee('Normal User');
    }

    public function test_user_can_login_by_formatted_mobile_and_localized_pin(): void
    {
        $user = $this->user(User::ROLE_BUSINESS_USER, '0536308965', 'business@example.test');

        $this->post('/login', [
            'identity' => '০৫৩৬ ৩০৮ ৯৬৫',
            'credential' => '১২৩৪৫৬',
            'language' => 'bn',
        ])->assertRedirect(route('safa.app'));

        $this->assertAuthenticatedAs($user);
    }

    public function test_unknown_wrong_and_inactive_login_share_one_compact_generic_failure(): void
    {
        $active = $this->user(User::ROLE_USER, '0536308965', 'active@example.test');
        $inactive = $this->user(User::ROLE_USER, '0536308966', 'inactive@example.test');
        $inactive->forceFill(['is_activated' => false])->saveQuietly();

        $cases = [
            ['unknown@example.test', '123456'],
            [$active->email, '654321'],
            [$inactive->email, '123456'],
        ];

        foreach ($cases as [$identity, $credential]) {
            $this->from('/login')->post('/login', [
                'identity' => $identity,
                'credential' => $credential,
                'language' => 'en',
            ])->assertRedirect('/login')->assertSessionHasErrors([
                'auth' => 'Sign-in failed.',
            ]);

            $this->get('/login?lang=en')
                ->assertOk()
                ->assertSee('Sign-in failed.')
                ->assertSee('class="auth-error-inline"', false)
                ->assertDontSee('The sign-in details are not valid. Please try again.')
                ->assertDontSee('The provided sign-in details are not valid.')
                ->assertSee('value="' . $identity . '"', false)
                ->assertDontSee('value="' . $credential . '"', false);

            $this->assertGuest();
        }
    }

    public function test_bengali_login_failure_uses_equally_short_generic_feedback(): void
    {
        $this->from('/login?lang=bn')->post('/login', [
            'identity' => 'unknown@example.test',
            'credential' => '123456',
            'language' => 'bn',
        ])->assertRedirect('/login?lang=bn')->assertSessionHasErrors('auth');

        $this->get('/login?lang=bn')
            ->assertOk()
            ->assertSee('লগইন ব্যর্থ।')
            ->assertDontSee('লগইন তথ্য সঠিক নয়। আবার চেষ্টা করুন।');
    }

    public function test_normal_user_cannot_access_supplier_transaction_or_wallet_web_routes(): void
    {
        $user = $this->user(User::ROLE_USER, '0536308965', 'normal@example.test');
        $this->actingAs($user);

        $this->getJson('/app/api/suppliers')->assertForbidden();
        $this->getJson('/app/api/transactions')->assertForbidden();
        $this->getJson('/app/api/wallet-ledgers')->assertForbidden();
        $this->getJson('/app/api/customers')->assertOk();
        $this->getJson('/app/api/expenses')->assertOk();
    }

    public function test_business_user_has_supplier_and_transaction_access_but_not_wallet_or_admin_settings(): void
    {
        $user = $this->user(User::ROLE_BUSINESS_USER, '0536308966', 'business@example.test');
        $this->actingAs($user);

        $this->getJson('/app/api/customers')->assertOk();
        $this->getJson('/app/api/suppliers')->assertOk();
        $this->getJson('/app/api/transactions')->assertOk();
        $this->getJson('/app/api/expenses')->assertOk();
        $this->getJson('/app/api/wallet-ledgers')->assertForbidden();
        $this->postJson('/app/api/config', ['app_name' => 'Nope'])->assertForbidden();
    }

    public function test_admin_web_page_exposes_settings_management_but_normal_user_does_not(): void
    {
        $admin = $this->user(User::ROLE_ADMIN, '0536308967', 'admin@example.test');
        $normal = $this->user(User::ROLE_USER, '0536308968', 'normal2@example.test');

        $this->actingAs($admin)
            ->get('/app')
            ->assertOk()
            ->assertSee('User Management')
            ->assertSee('Brand & Business Configuration');

        auth()->logout();

        $this->actingAs($normal)
            ->get('/app')
            ->assertOk()
            ->assertDontSee('User Management')
            ->assertDontSee('Supplier rate')
            ->assertDontSee('data-nav="wallet"', false);
    }
}
