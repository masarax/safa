<?php

namespace Tests\Feature;

use App\Http\Middleware\CheckInstalled;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class WebExpensePermissionTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware(CheckInstalled::class);
    }

    private function restrictedMember(): array
    {
        $owner = User::factory()->create(['role' => User::ROLE_SUPERADMIN, 'is_activated' => true]);
        $member = User::factory()->create(['role' => User::ROLE_ADMIN, 'is_activated' => true]);
        $account = Account::create(['name' => 'Expense Restricted', 'balance' => 0, 'owner_user_id' => $owner->id]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'account_id' => $account->id,
            'shared_with_user_id' => $member->id,
            'permissions_override' => ['can_manage_expenses' => false],
        ]);
        return [$member, $account];
    }

    public function test_restricted_share_cannot_use_any_web_expense_route(): void
    {
        [$member, $account] = $this->restrictedMember();
        $headers = ['X-SAFA-ACCOUNT-ID' => (string) $account->id];

        $this->actingAs($member)->withHeaders($headers)->getJson('/app/api/expenses')->assertForbidden();
        $this->actingAs($member)->withHeaders($headers)->postJson('/app/api/expenses', [])->assertForbidden();
        $this->actingAs($member)->withHeaders($headers)->putJson('/app/api/expenses/1', [])->assertForbidden();
        $this->actingAs($member)->withHeaders($headers)->deleteJson('/app/api/expenses/1?confirmed=true')->assertForbidden();
    }

    public function test_web_shell_uses_effective_share_permissions(): void
    {
        [$member, $account] = $this->restrictedMember();

        $response = $this->actingAs($member)
            ->withSession(['safa_active_account_id' => $account->id])
            ->get('/app')
            ->assertOk();

        $response->assertSee('data-expenses-url=""', false);
        $response->assertDontSee('data-nav="expenses"', false);
    }
}
