<?php

namespace Tests\Feature;

use App\Support\OperationalMetrics;
use Tests\TestCase;

class OperationalObservabilityTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        config(['observability.ops_key' => 'test-ops-key']);
        config(['safa.mobile_client_key' => 'test-mobile-client']);
        OperationalMetrics::resetForTests();
    }

    protected function tearDown(): void
    {
        OperationalMetrics::resetForTests();
        parent::tearDown();
    }

    public function test_api_requests_receive_safe_correlation_id_and_latency_metrics(): void
    {
        $health = $this->getJson('/api/auth/health');
        $health->assertHeader('X-SAFA-REQUEST-ID');

        $this->getJson('/api/ops/metrics')->assertNotFound();
        $metrics = $this->withHeader('X-SAFA-OPS-KEY', 'test-ops-key')->getJson('/api/ops/metrics')->assertOk();

        $requests = $metrics->json('requests');
        $this->assertIsArray($requests);
        $this->assertNotEmpty($requests);
        $this->assertTrue((bool) $metrics->json('database.healthy'));
        foreach ($requests as $entry) {
            $this->assertArrayHasKey('latency_ms', $entry);
            $this->assertArrayHasKey('p50', $entry['latency_ms']);
            $this->assertArrayHasKey('p95', $entry['latency_ms']);
            $this->assertArrayHasKey('p99', $entry['latency_ms']);
        }
    }

    public function test_mobile_telemetry_accepts_only_bounded_dimensions_and_drops_arbitrary_secrets(): void
    {
        $this->withHeader('X-SAFA-API-KEY', 'test-mobile-client')->postJson('/api/telemetry/mobile', [
            'event_type' => 'sync_failure',
            'release' => '1.0.42',
            'endpoint' => 'sync/down',
            'reason' => 'http_503',
            'duration_ms' => 3200,
            'pending_count' => 7,
            'oldest_pending_seconds' => 120,
            'access_token' => 'must-never-be-retained',
            'receiver_account' => '999999999999',
        ])->assertStatus(202)->assertExactJson(['status' => 'accepted']);

        $snapshot = OperationalMetrics::snapshot();
        $encoded = json_encode($snapshot);
        $this->assertStringContainsString('sync_failure', $encoded);
        $this->assertStringContainsString('1.0.42', $encoded);
        $this->assertStringNotContainsString('must-never-be-retained', $encoded);
        $this->assertStringNotContainsString('999999999999', $encoded);
    }

    public function test_mobile_telemetry_rejects_free_text_dimensions(): void
    {
        $this->withHeader('X-SAFA-API-KEY', 'test-mobile-client')->postJson('/api/telemetry/mobile', [
            'event_type' => 'crash',
            'release' => '1.0.42',
            'reason' => 'token=secret value',
        ])->assertStatus(422);
    }
}
