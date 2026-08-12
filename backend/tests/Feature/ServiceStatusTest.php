<?php

namespace Tests\Feature;

use Tests\TestCase;

class ServiceStatusTest extends TestCase
{
    public function test_public_root_reports_service_status_without_exposing_private_controls(): void
    {
        $response = $this->getJson('/');

        $response->assertOk()
            ->assertJson([
                'status' => 'ok',
                'service' => 'SAFA',
            ])
            ->assertJsonMissingPath('database')
            ->assertJsonMissingPath('credentials')
            ->assertJsonMissingPath('environment');
    }

    public function test_api_health_is_unauthenticated_and_stable(): void
    {
        $this->getJson('/api/auth/health')
            ->assertOk()
            ->assertExactJson([
                'status' => 'ok',
                'service' => 'SAFA API',
            ]);
    }
}
