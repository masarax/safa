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

    public function test_owner_switches_a_to_b_and_subsequent_context_uses_b(): void
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
}
