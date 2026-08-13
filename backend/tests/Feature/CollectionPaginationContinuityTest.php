<?php

namespace Tests\Feature;

use App\Http\Controllers\CustomerController;
use App\Http\Controllers\SupplierController;
use App\Models\Account;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class CollectionPaginationContinuityTest extends TestCase
{
    use RefreshDatabase;

    public function test_customer_pages_are_contiguous_unique_and_end_with_empty_page(): void
    {
        [$user, $account] = $this->account();
        foreach (range(1, 105) as $i) {
            Customer::create([
                'account_id' => $account->id,
                'local_id' => $i,
                'name' => sprintf('Customer %03d', $i),
                'timestamp' => time(),
            ]);
        }

        $page1 = $this->customers($user, $account->id, 1, 50);
        $page2 = $this->customers($user, $account->id, 2, 50);
        $page3 = $this->customers($user, $account->id, 3, 50);
        $page4 = $this->customers($user, $account->id, 4, 50);

        $ids = array_merge(
            array_column($page1['customers'], 'id'),
            array_column($page2['customers'], 'id'),
            array_column($page3['customers'], 'id')
        );
        $this->assertCount(105, $ids);
        $this->assertCount(105, array_unique($ids));
        $this->assertCount(50, $page1['customers']);
        $this->assertCount(50, $page2['customers']);
        $this->assertCount(5, $page3['customers']);
        $this->assertFalse($page3['pagination']['has_more']);
        $this->assertSame([], $page4['customers']);
        $this->assertFalse($page4['pagination']['has_more']);
    }

    public function test_supplier_pages_are_contiguous_and_terminal_page_is_empty(): void
    {
        [$user, $account] = $this->account();
        foreach (range(1, 12) as $i) {
            Supplier::create([
                'account_id' => $account->id,
                'local_id' => $i,
                'name' => sprintf('Supplier %03d', $i),
                'timestamp' => time(),
            ]);
        }

        $page1 = $this->suppliers($user, $account->id, 1, 10);
        $page2 = $this->suppliers($user, $account->id, 2, 10);
        $page3 = $this->suppliers($user, $account->id, 3, 10);

        $this->assertCount(10, $page1['suppliers']);
        $this->assertCount(2, $page2['suppliers']);
        $this->assertFalse($page2['pagination']['has_more']);
        $this->assertSame([], $page3['suppliers']);
        $this->assertFalse($page3['pagination']['has_more']);
    }

    private function customers(User $user, int $accountId, int $page, int $perPage): array
    {
        $request = Request::create('/customers', 'GET', compact('page', 'perPage') + [
            'account_id' => $accountId,
            'per_page' => $perPage,
        ]);
        $request->setUserResolver(fn () => $user);
        return (new CustomerController())->index($request)->getData(true);
    }

    private function suppliers(User $user, int $accountId, int $page, int $perPage): array
    {
        $request = Request::create('/suppliers', 'GET', [
            'account_id' => $accountId,
            'page' => $page,
            'per_page' => $perPage,
        ]);
        $request->setUserResolver(fn () => $user);
        return (new SupplierController())->index($request)->getData(true);
    }

    private function account(): array
    {
        $user = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Primary', 'balance' => 0, 'owner_user_id' => $user->id]);
        return [$user, $account];
    }
}
