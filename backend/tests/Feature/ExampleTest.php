<?php

namespace Tests\Feature;

use Tests\TestCase;

class ExampleTest extends TestCase
{
    public function test_private_service_root_is_not_a_public_welcome_page(): void
    {
        $response = $this->get('/');
        $response->assertStatus(404)->assertJsonPath('status', 'not_found');
    }
}
