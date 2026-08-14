<?php

namespace Tests\Feature;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class ServiceStatusTest extends TestCase
{
    use RefreshDatabase;

    public function test_private_root_does_not_expose_a_public_service_status_page(): void
    {
        $response = $this->getJson('/');

        $response->assertNotFound()
            ->assertExactJson([
                'status' => 'not_found',
            ]);
    }

    public function test_api_health_is_unauthenticated_and_stable(): void
    {
        $this->getJson('/api/auth/health')
            ->assertOk()
            ->assertExactJson([
                'status' => 'ok',
                'service' => 'SAFA API',
                'build' => 'development',
                'checks' => [
                    'runtime' => true,
                    'database' => true,
                    'schema' => true,
                    'cache' => true,
                    'storage' => true,
                    'build' => true,
                ],
            ]);
    }

    public function test_api_health_fails_when_a_required_financial_migration_is_missing(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->dropColumn('sar_collected');
        });

        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJsonPath('status', 'degraded')
            ->assertJsonPath('checks.database', true)
            ->assertJsonPath('checks.schema', false);
    }
}
