<?php

namespace Tests\Feature;

use App\Http\Controllers\GraphQLController;
use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class GraphQLSoftDeleteVisibilityTest extends TestCase
{
    use RefreshDatabase;

    public function test_deprecated_graphql_surface_cannot_return_active_or_deleted_rows(): void
    {
        $owner = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Primary', 'balance' => 0, 'owner_user_id' => $owner->id]);
        Customer::create(['account_id' => $account->id, 'local_id' => 1, 'name' => 'Active', 'timestamp' => time()]);
        $deleted = Customer::create(['account_id' => $account->id, 'local_id' => 2, 'name' => 'Deleted', 'timestamp' => time()]);
        $deleted->delete();

        $request = Request::create('/graphql', 'POST', ['query' => '{ customers { local_id name } }']);
        $request->attributes->set('active_account_id', $account->id);
        $response = (new GraphQLController())->handle($request);
        $payload = $response->getData(true);

        $this->assertSame(410, $response->getStatusCode());
        $this->assertArrayNotHasKey('data', $payload);
        $this->assertSame('GRAPHQL_DEPRECATED', $payload['errors'][0]['extensions']['code']);
    }
}
