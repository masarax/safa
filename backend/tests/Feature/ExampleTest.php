<?php

namespace Tests\Feature;

use Tests\TestCase;

class ExampleTest extends TestCase
{
    public function test_service_root_routes_browser_users_to_secure_login(): void
    {
        $this->get('/')
            ->assertRedirect(route('safa.login'));

        $this->get('/login')
            ->assertOk()
            ->assertSee('SAFA')
            ->assertHeader('X-Content-Type-Options', 'nosniff');
    }
}
