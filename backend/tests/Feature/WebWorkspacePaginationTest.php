<?php

namespace Tests\Feature;

use App\Http\Controllers\WebWorkspaceController;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class WebWorkspacePaginationTest extends TestCase
{
    use RefreshDatabase;

    public function test_large_workspace_initial_snapshot_is_bounded(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $this->insertCustomers($account->id, 240);
        $this->insertTransactions($account->id, 180);
        $this->insertExpenses($account->id, 130);

        $response = app(WebWorkspaceController::class)->index($this->request([
            'account_id' => $account->id,
        ], $user));
        $payload = $response->getData(true);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertCount(50, $payload['customers']);
        $this->assertCount(50, $payload['transactions']);
        $this->assertCount(50, $payload['expenses']);
        $this->assertSame(240, $payload['pagination']['customers']['total']);
        $this->assertSame(180, $payload['pagination']['transactions']['total']);
        $this->assertSame(130, $payload['pagination']['expenses']['total']);
        $this->assertTrue($payload['pagination']['customers']['has_more']);
    }

    public function test_collection_pages_are_deterministic_and_do_not_overlap(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $this->insertCustomers($account->id, 135);
        $controller = app(WebWorkspaceController::class);

        $first = $controller->index($this->request([
            'account_id' => $account->id,
            'collection' => 'customers',
            'page' => 1,
            'per_page' => 40,
        ], $user))->getData(true);
        $second = $controller->index($this->request([
            'account_id' => $account->id,
            'collection' => 'customers',
            'page' => 2,
            'per_page' => 40,
        ], $user))->getData(true);

        $firstIds = array_column($first['items'], 'id');
        $secondIds = array_column($second['items'], 'id');
        $this->assertCount(40, $firstIds);
        $this->assertCount(40, $secondIds);
        $this->assertSame([], array_values(array_intersect($firstIds, $secondIds)));
        $this->assertSame(135, $second['pagination']['total']);
        $this->assertSame(2, $second['pagination']['page']);
        $this->assertTrue($second['pagination']['has_more']);
    }

    public function test_page_size_is_capped_even_when_client_requests_unbounded_page(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $this->insertCustomers($account->id, 150);

        $payload = app(WebWorkspaceController::class)->index($this->request([
            'account_id' => $account->id,
            'collection' => 'customers',
            'per_page' => 10000,
        ], $user))->getData(true);

        $this->assertCount(100, $payload['items']);
        $this->assertSame(100, $payload['pagination']['per_page']);
        $this->assertTrue($payload['pagination']['has_more']);
    }

    public function test_share_permission_is_rechecked_for_each_paged_collection(): void
    {
        [$owner, $account] = $this->ownerAndAccount();
        $member = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
            'mobile' => '0500099999',
        ]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $member->id,
            'account_id' => $account->id,
            'permissions_override' => ['can_view_customers' => false],
        ]);
        $this->insertCustomers($account->id, 10);

        $response = app(WebWorkspaceController::class)->index($this->request([
            'account_id' => $account->id,
            'collection' => 'customers',
        ], $member));

        $this->assertSame(403, $response->getStatusCode());
        $this->assertSame('error', $response->getData(true)['status']);
    }

    private function ownerAndAccount(): array
    {
        $user = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
            'mobile' => '0500000169',
        ]);
        $account = Account::create([
            'name' => 'Large Workspace',
            'balance' => 0,
            'owner_user_id' => $user->id,
        ]);

        return [$user, $account];
    }

    private function insertCustomers(int $accountId, int $count): void
    {
        $now = now();
        $rows = [];
        for ($i = 1; $i <= $count; $i++) {
            $rows[] = [
                'account_id' => $accountId,
                'local_id' => 100000 + $i,
                'name' => 'Customer ' . $i,
                'phone' => '05' . str_pad((string) $i, 8, '0', STR_PAD_LEFT),
                'timestamp' => 1_700_000_000 + $i,
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }
        foreach (array_chunk($rows, 100) as $chunk) DB::table('customers')->insert($chunk);
    }

    private function insertTransactions(int $accountId, int $count): void
    {
        $now = now();
        $rows = [];
        for ($i = 1; $i <= $count; $i++) {
            $rows[] = [
                'account_id' => $accountId,
                'local_id' => 200000 + $i,
                'type' => 'Delivered',
                'amount' => '1.00',
                'amount_sar' => '1.00',
                'customer_rate' => '1.0000',
                'supplier_rate' => '1.0000',
                'amount_bdt' => '1.00',
                'sar_collected' => '1.00',
                'bdt_disbursed' => '1.00',
                'timestamp' => 1_700_100_000 + $i,
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }
        foreach (array_chunk($rows, 100) as $chunk) DB::table('transactions')->insert($chunk);
    }

    private function insertExpenses(int $accountId, int $count): void
    {
        $now = now();
        $rows = [];
        for ($i = 1; $i <= $count; $i++) {
            $rows[] = [
                'account_id' => $accountId,
                'local_id' => 300000 + $i,
                'title' => 'Expense ' . $i,
                'amount' => '1.00',
                'currency' => 'BDT',
                'is_expense' => true,
                'category' => 'General',
                'timestamp' => 1_700_200_000 + $i,
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }
        foreach (array_chunk($rows, 100) as $chunk) DB::table('expenses_incomes')->insert($chunk);
    }

    private function request(array $query, User $user): Request
    {
        $request = Request::create('/app/api/mobile/workspace', 'GET', $query);
        $request->setUserResolver(fn () => $user);
        return $request;
    }
}
