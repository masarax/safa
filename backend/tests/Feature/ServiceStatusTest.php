<?php

namespace Tests\Feature;

use Tests\TestCase;

class ServiceStatusTest extends TestCase
{
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
            ]);
    }
}
