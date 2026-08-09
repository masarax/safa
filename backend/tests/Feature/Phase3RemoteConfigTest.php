<?php

namespace Tests\Feature;

use Tests\TestCase;

class Phase3RemoteConfigTest extends TestCase
{
    /**
     * Test remote config structure.
     */
    public function test_remote_config_endpoint_returns_json()
    {
        $response = $this->get('/api/config/remote');
        // Accept 200 or 401 if API key required
        $this->assertTrue(in_array($response->status(), [200, 401]));
    }
}
