<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ApiVersioningContractTest extends TestCase
{
    use RefreshDatabase;

    public function test_v1_health_proxy_matches_legacy_health_contract(): void
    {
        $legacy = $this->getJson('/api/auth/health')->assertOk()->json();
        $v1 = $this->getJson('/api/v1/auth/health')->assertOk()->json();

        $this->assertSame($legacy, $v1);
        $this->assertSame('ok', $v1['status']);
        $this->assertSame('SAFA API', $v1['service']);
    }

    public function test_v1_business_collection_cannot_bypass_security(): void
    {
        $legacy = $this->getJson('/api/customers');
        $v1 = $this->getJson('/api/v1/customers');

        $this->assertContains($legacy->getStatusCode(), [401, 403, 419, 429]);
        $this->assertContains($v1->getStatusCode(), [401, 403, 419, 429]);
    }

    public function test_unknown_v1_route_is_not_found(): void
    {
        $this->getJson('/api/v1/definitely-not-a-safa-route')->assertNotFound();
    }

    public function test_recursive_v1_proxy_path_is_rejected(): void
    {
        $this->getJson('/api/v1/v1/auth/health')->assertNotFound();
    }
}
