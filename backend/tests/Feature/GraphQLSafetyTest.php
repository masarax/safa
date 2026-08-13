<?php

namespace Tests\Feature;

use App\Http\Controllers\GraphQLController;
use App\Http\Middleware\ResolveGraphQLAccountContext;
use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class GraphQLSafetyTest extends TestCase
{
    use RefreshDatabase;

    public function test_collection_without_limit_is_bounded_to_default_page_size(): void
    {
        $account = $this->account();
        foreach (range(1, 110) as $i) {
            Customer::create([
                'account_id' => $account->id,
                'local_id' => $i,
                'name' => sprintf('Customer %03d', $i),
                'timestamp' => time(),
            ]);
        }

        $payload = $this->graphql($account->id, '{ customers { id name } }');
        $this->assertCount(100, $payload['data']['customers']);
    }

    public function test_excessive_limit_is_clamped_to_server_maximum(): void
    {
        $account = $this->account();
        foreach (range(1, 260) as $i) {
            Customer::create([
                'account_id' => $account->id,
                'local_id' => $i,
                'name' => sprintf('Customer %03d', $i),
                'timestamp' => time(),
            ]);
        }

        $payload = $this->graphql($account->id, '{ customers(limit: 9999) { id } }');
        $this->assertCount(250, $payload['data']['customers']);
    }

    public function test_invalid_pagination_is_rejected_without_loading_collection(): void
    {
        $account = $this->account();

        $negativeOffset = $this->graphql($account->id, '{ customers(offset: -1) { id } }');
        $this->assertNull($negativeOffset['data']['customers']);
        $this->assertSame('offset must be a non-negative integer.', $negativeOffset['errors'][0]['message']);

        $zeroLimit = $this->graphql($account->id, '{ customers(limit: 0) { id } }');
        $this->assertNull($zeroLimit['data']['customers']);
        $this->assertSame('limit must be a positive integer.', $zeroLimit['errors'][0]['message']);
    }

    public function test_normal_reads_exclude_soft_deleted_and_other_account_rows(): void
    {
        $account = $this->account();
        $other = $this->account('Other');

        Customer::create(['account_id' => $account->id, 'local_id' => 1, 'name' => 'Visible', 'timestamp' => time()]);
        $deleted = Customer::create(['account_id' => $account->id, 'local_id' => 2, 'name' => 'Deleted', 'timestamp' => time()]);
        $deleted->delete();
        Customer::create(['account_id' => $other->id, 'local_id' => 3, 'name' => 'Other Account', 'timestamp' => time()]);

        $payload = $this->graphql($account->id, '{ customers { id name } }');
        $names = array_column($payload['data']['customers'], 'name');

        $this->assertSame(['Visible'], $names);
    }

    public function test_graphql_business_mutations_are_deprecated_in_favor_of_rest(): void
    {
        $account = $this->account();
        $request = Request::create('/graphql', 'POST', [
            'query' => 'mutation { registerCustomer(name: "Nope") { id } }',
        ]);
        $request->attributes->set('active_account_id', $account->id);

        $response = (new GraphQLController())->handle($request);
        $payload = $response->getData(true);

        $this->assertSame(410, $response->getStatusCode());
        $this->assertSame('GRAPHQL_MUTATIONS_DEPRECATED', $payload['errors'][0]['extensions']['code']);
        $this->assertSame('/api/v1', $payload['errors'][0]['extensions']['rest_base']);
    }

    public function test_multi_account_user_requires_explicit_graphql_account_context(): void
    {
        $user = User::factory()->create(['is_activated' => true]);
        Account::create(['name' => 'A', 'balance' => 0, 'owner_user_id' => $user->id]);
        Account::create(['name' => 'B', 'balance' => 0, 'owner_user_id' => $user->id]);

        $request = Request::create('/graphql', 'POST');
        $request->setUserResolver(fn () => $user);

        $response = (new ResolveGraphQLAccountContext())->handle($request, fn () => response()->json(['ok' => true]));
        $payload = $response->getData(true);

        $this->assertSame(409, $response->getStatusCode());
        $this->assertSame('ACCOUNT_CONTEXT_REQUIRED', $payload['code']);
    }

    private function graphql(int $accountId, string $query): array
    {
        $request = Request::create('/graphql', 'POST', ['query' => $query]);
        $request->attributes->set('active_account_id', $accountId);
        return (new GraphQLController())->handle($request)->getData(true);
    }

    private function account(string $name = 'Primary'): Account
    {
        $owner = User::factory()->create(['is_activated' => true]);
        return Account::create(['name' => $name, 'balance' => 0, 'owner_user_id' => $owner->id]);
    }
}
