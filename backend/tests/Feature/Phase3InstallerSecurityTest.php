<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class Phase3InstallerSecurityTest extends TestCase
{
    use RefreshDatabase;

    public function test_public_database_update_endpoint_is_not_exposed(): void
    {
        $this->postJson('/update-db')->assertNotFound();
        $this->postJson('/update-db', ['key' => 'anything'])->assertNotFound();
        $this->get('/update-db')->assertNotFound();
    }

    public function test_retired_installer_database_and_update_processes_are_not_exposed(): void
    {
        $this->get('/install/test-db')->assertNotFound();
        $this->post('/install/test-db')->assertNotFound();
        $this->post('/install/process')->assertNotFound();
        $this->get('/install/update')->assertNotFound();
        $this->post('/install/update-process')->assertNotFound();
    }

    public function test_update_controls_cannot_be_reached_by_session_spoofing(): void
    {
        $this->withSession(['user_id' => 9999, 'safa_update_token' => 'spoofed'])
            ->post('/install/update-process', ['update_token' => 'spoofed'])
            ->assertNotFound();
    }
}
