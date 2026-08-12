<?php

namespace Tests\Feature;

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
}
