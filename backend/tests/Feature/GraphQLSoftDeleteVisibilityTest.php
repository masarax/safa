<?php

namespace Tests\Feature;

use App\Http\Controllers\GraphQLController;
use App\Models\Account;
use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class GraphQLSoftDeleteVisibilityTest extends TestCase
{
    use RefreshDatabase;

    public function test_normal_graphql_reads_hide_deleted_business_rows(): void
    {
        $owner = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Primary', 'balance' => 0, 'owner_user_id' => $owner->id]);

        Customer::create(['account_id' => $account->id, 'local_id' => 1, 'name' => 'Customer Active', 'timestamp' => time()]);
        $customerDeleted = Customer::create(['account_id' => $account->id, 'local_id' => 2, 'name' => 'Customer Deleted', 'timestamp' => time()]);
        $customerDeleted->delete();

        Supplier::create(['account_id' => $account->id, 'local_id' => 11, 'name' => 'Supplier Active', 'timestamp' => time()]);
        $supplierDeleted = Supplier::create(['account_id' => $account->id, 'local_id' => 12, 'name' => 'Supplier Deleted', 'timestamp' => time()]);
        $supplierDeleted->delete();

        Transaction::create(['account_id' => $account->id, 'local_id' => 21, 'type' => 'REMITTANCE', 'amount' => 100, 'timestamp' => time()]);
        $transactionDeleted = Transaction::create(['account_id' => $account->id, 'local_id' => 22, 'type' => 'REMITTANCE', 'amount' => 200, 'timestamp' => time()]);
        $transactionDeleted->delete();

        WalletBatch::create(['account_id' => $account->id, 'local_id' => 31, 'rate' => 32.5, 'initial_bdt' => 1000, 'remaining_bdt' => 800, 'timestamp' => time()]);
        $walletDeleted = WalletBatch::create(['account_id' => $account->id, 'local_id' => 32, 'rate' => 32.5, 'initial_bdt' => 1000, 'remaining_bdt' => 500, 'timestamp' => time()]);
        $walletDeleted->delete();

        ExpenseIncome::create(['account_id' => $account->id, 'local_id' => 41, 'title' => 'Expense Active', 'amount' => 50, 'currency' => 'BDT', 'is_expense' => true, 'category' => 'General', 'timestamp' => time()]);
        $expenseDeleted = ExpenseIncome::create(['account_id' => $account->id, 'local_id' => 42, 'title' => 'Expense Deleted', 'amount' => 75, 'currency' => 'BDT', 'is_expense' => true, 'category' => 'General', 'timestamp' => time()]);
        $expenseDeleted->delete();

        $request = Request::create('/graphql', 'POST', [
            'query' => '{ customers { local_id name } suppliers { local_id name } transactions { local_id } walletBatches { local_id } expensesIncomes { local_id title } }',
        ]);
        $request->attributes->set('active_account_id', $account->id);
        $payload = (new GraphQLController())->handle($request)->getData(true);

        $this->assertSame([1], array_column($payload['data']['customers'], 'local_id'));
        $this->assertSame([11], array_column($payload['data']['suppliers'], 'local_id'));
        $this->assertSame([21], array_column($payload['data']['transactions'], 'local_id'));
        $this->assertSame([31], array_column($payload['data']['walletBatches'], 'local_id'));
        $this->assertSame([41], array_column($payload['data']['expensesIncomes'], 'local_id'));
    }
}
