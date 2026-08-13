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

class CollectionPaginationTest extends TestCase
{
    use RefreshDatabase;

    public function test_customer_collection_defaults_to_fifty_and_keeps_account_isolation(): void
    {
        [$user, $account, $otherAccount] = $this->accounts();
        foreach (range(1, 60) as $i) {
            Customer::create(['account_id' => $account->id, 'local_id' => $i, 'name' => sprintf('Customer %03d', $i), 'timestamp' => time()]);
        }
        Customer::create(['account_id' => $otherAccount->id, 'local_id' => 999, 'name' => 'Other Account', 'timestamp' => time()]);

        $request = Request::create('/customers', 'GET', ['account_id' => $account->id]);
        $request->setUserResolver(fn () => $user);
        $response = (new CustomerController())->index($request);
        $payload = $response->getData(true);

        $this->assertCount(50, $payload['customers']);
        $this->assertSame(50, $payload['pagination']['per_page']);
        $this->assertTrue($payload['pagination']['has_more']);
        $this->assertNotContains('Other Account', array_column($payload['customers'], 'name'));
    }

    public function test_customer_collection_rejects_page_size_above_server_maximum(): void
    {
        [$user, $account] = $this->accounts();
        $request = Request::create('/customers', 'GET', ['account_id' => $account->id, 'per_page' => 201]);
        $request->setUserResolver(fn () => $user);

        $response = (new CustomerController())->index($request);
        $this->assertSame(422, $response->getStatusCode());
    }

    public function test_supplier_collection_is_bounded_and_excludes_soft_deleted_rows(): void
    {
        [$user, $account] = $this->accounts();
        foreach (range(1, 55) as $i) {
            Supplier::create(['account_id' => $account->id, 'local_id' => $i, 'name' => sprintf('Supplier %03d', $i), 'timestamp' => time()]);
        }
        $deleted = Supplier::create(['account_id' => $account->id, 'local_id' => 1000, 'name' => 'Deleted Supplier', 'timestamp' => time()]);
        $deleted->delete();

        $request = Request::create('/suppliers', 'GET', ['account_id' => $account->id]);
        $request->setUserResolver(fn () => $user);
        $response = (new SupplierController())->index($request);
        $payload = $response->getData(true);

        $this->assertCount(50, $payload['suppliers']);
        $this->assertTrue($payload['pagination']['has_more']);
        $this->assertNotContains('Deleted Supplier', array_column($payload['suppliers'], 'name'));
    }

    private function accounts(): array
    {
        $user = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Primary', 'balance' => 0, 'owner_user_id' => $user->id]);
        $otherOwner = User::factory()->create(['is_activated' => true]);
        $otherAccount = Account::create(['name' => 'Other', 'balance' => 0, 'owner_user_id' => $otherOwner->id]);
        return [$user, $account, $otherAccount];
    }
}
