<?php

namespace Tests\Feature;

use App\Http\Controllers\AccountContextController;
use App\Http\Controllers\AuthorizeAccountContext;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class AccountContextIsolationTest extends TestCase
{
    use RefreshDatabase;
    use AuthorizeAccountContext;

    public function test_shared_access_is_scoped_to_the_exact_account(): void
    {
        $owner = User::factory()->create(['is_activated' => true]);
        $sharedUser = User::factory()->create(['is_activated' => true]);
        $accountA = Account::create(['name' => 'Owner Account A', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $accountB = Account::create(['name' => 'Owner Account B', 'balance' => 0, 'owner_user_id' => $owner->id]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $sharedUser->id,
            'account_id' => $accountA->id,
            'permissions_override' => ['can_view_customers' => true],
        ]);

        $allowed = $this->resolveAuthorizedAccountContext(
            Request::create('/', 'GET', ['account_id' => $accountA->id])
                ->setUserResolver(fn () => $sharedUser)
        );
        $this->assertSame($accountA->id, $allowed['account_id']);

        $denied = $this->resolveAuthorizedAccountContext(
            Request::create('/', 'GET', ['account_id' => $accountB->id])
                ->setUserResolver(fn () => $sharedUser)
        );
        $this->assertArrayHasKey('error', $denied);
        $this->assertSame(403, $denied['error']->getStatusCode());
    }

    public function test_multi_account_listing_does_not_require_an_active_account_first(): void
    {
        $owner = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $accountA = Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $accountB = Account::create(['name' => 'B', 'balance' => 0, 'owner_user_id' => $owner->id]);

        $request = Request::create('/api/v1/accounts', 'GET');
        $request->setUserResolver(fn () => $owner);

        $response = app(AccountContextController::class)->index($request);
        $payload = $response->getData(true);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertNull($payload['active_account_id']);
        $this->assertSame(
            [$accountA->id, $accountB->id],
            collect($payload['accounts'])->pluck('account_id')->sort()->values()->all()
        );
    }

    public function test_single_account_listing_bootstraps_that_account_as_active_choice(): void
    {
        $owner = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $account = Account::create(['name' => 'Only', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $request = Request::create('/api/v1/accounts', 'GET');
        $request->setUserResolver(fn () => $owner);

        $payload = app(AccountContextController::class)->index($request)->getData(true);

        $this->assertSame($account->id, $payload['active_account_id']);
        $this->assertCount(1, $payload['accounts']);
    }

    public function test_owner_switches_a_to_b_and_subsequent_session_context_uses_b(): void
    {
        $owner = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $accountA = Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $accountB = Account::create(['name' => 'B', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $session = app('session')->driver();
        $session->start();
        $session->put('safa_active_account_id', $accountA->id);

        $switchRequest = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => $accountB->id]);
        $switchRequest->setUserResolver(fn () => $owner);
        $switchRequest->setLaravelSession($session);

        $response = app(AccountContextController::class)->switch($switchRequest);
        $payload = $response->getData(true);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame($accountB->id, $payload['active_account_id']);
        $this->assertSame($accountB->id, $session->get('safa_active_account_id'));

        $nextRequest = Request::create('/api/v1/customers', 'GET');
        $nextRequest->setUserResolver(fn () => $owner);
        $nextRequest->setLaravelSession($session);
        $context = $this->resolveAuthorizedAccountContext($nextRequest);

        $this->assertSame($accountB->id, $context['account_id']);
    }

    public function test_stateless_switch_round_trip_uses_explicit_account_header(): void
    {
        $owner = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $accountA = Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $accountB = Account::create(['name' => 'B', 'balance' => 0, 'owner_user_id' => $owner->id]);
        $controller = app(AccountContextController::class);

        $switchToB = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => $accountB->id]);
        $switchToB->setUserResolver(fn () => $owner);
        $responseB = $controller->switch($switchToB);
        $payloadB = $responseB->getData(true);

        $this->assertSame(200, $responseB->getStatusCode());
        $this->assertSame($accountB->id, $payloadB['active_account_id']);
        $this->assertSame('X-SAFA-ACCOUNT-ID', $payloadB['context_header']);

        $next = Request::create('/api/v1/customers', 'GET');
        $next->headers->set('X-SAFA-ACCOUNT-ID', (string) $payloadB['active_account_id']);
        $next->setUserResolver(fn () => $owner);
        $this->assertSame($accountB->id, $this->resolveAuthorizedAccountContext($next)['account_id']);

        $repeatB = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => $accountB->id]);
        $repeatB->setUserResolver(fn () => $owner);
        $this->assertSame($accountB->id, $controller->switch($repeatB)->getData(true)['active_account_id']);

        $switchToA = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => $accountA->id]);
        $switchToA->setUserResolver(fn () => $owner);
        $this->assertSame($accountA->id, $controller->switch($switchToA)->getData(true)['active_account_id']);
    }

    public function test_unauthorized_switch_does_not_change_existing_active_context(): void
    {
        $user = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $other = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        $accountA = Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $user->id]);
        $unrelated = Account::create(['name' => 'Unrelated', 'balance' => 0, 'owner_user_id' => $other->id]);
        $session = app('session')->driver();
        $session->start();
        $session->put('safa_active_account_id', $accountA->id);

        $request = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => $unrelated->id]);
        $request->setUserResolver(fn () => $user);
        $request->setLaravelSession($session);

        $response = app(AccountContextController::class)->switch($request);

        $this->assertSame(403, $response->getStatusCode());
        $this->assertSame($accountA->id, $session->get('safa_active_account_id'));
    }

    public function test_nonexistent_switch_target_fails_without_a_session_side_effect(): void
    {
        $user = User::factory()->create(['is_activated' => true, 'role' => 'staff']);
        Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $user->id]);
        $request = Request::create('/api/v1/accounts/switch', 'POST', ['account_id' => 999999]);
        $request->setUserResolver(fn () => $user);

        $response = app(AccountContextController::class)->switch($request);

        $this->assertSame(403, $response->getStatusCode());
    }

    public function test_legacy_and_canonical_switch_routes_use_the_same_controller_action(): void
    {
        $router = app('router');
        $legacy = $router->getRoutes()->match(Request::create('/api/auth/switch-account', 'POST'));
        $canonical = $router->getRoutes()->match(Request::create('/api/accounts/switch', 'POST'));

        $this->assertSame(AccountContextController::class . '@switch', $legacy->getActionName());
        $this->assertSame(AccountContextController::class . '@switch', $canonical->getActionName());
    }
}
