<?php

namespace Tests\Feature;

use App\Http\Controllers\GraphQLController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class GraphQLSafetyTest extends TestCase
{
    use RefreshDatabase;

    public function test_graphql_reads_are_deprecated_in_favor_of_versioned_rest(): void
    {
        $this->assertDeprecated('{ customers { id name } }');
    }

    public function test_graphql_mutations_are_deprecated_in_favor_of_versioned_rest(): void
    {
        $this->assertDeprecated('mutation { registerCustomer(name: "Nope") { id } }');
    }

    public function test_graphql_deprecation_response_never_contains_business_data(): void
    {
        $request = Request::create('/graphql', 'POST', ['query' => '{ transactions { id amount_sar } }']);
        $response = (new GraphQLController())->handle($request);
        $payload = $response->getData(true);

        $this->assertSame(410, $response->getStatusCode());
        $this->assertArrayNotHasKey('data', $payload);
        $this->assertSame('GRAPHQL_DEPRECATED', $payload['errors'][0]['extensions']['code']);
        $this->assertSame('/api/v1', $payload['errors'][0]['extensions']['rest_base']);
    }

    private function assertDeprecated(string $query): void
    {
        $request = Request::create('/graphql', 'POST', ['query' => $query]);
        $response = (new GraphQLController())->handle($request);
        $payload = $response->getData(true);

        $this->assertSame(410, $response->getStatusCode());
        $this->assertSame('GRAPHQL_DEPRECATED', $payload['errors'][0]['extensions']['code']);
        $this->assertSame('/api/v1', $payload['errors'][0]['extensions']['rest_base']);
    }
}
