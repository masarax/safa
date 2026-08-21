<?php

namespace Tests\Feature;

use Tests\TestCase;

class SystemErrorSurfaceTest extends TestCase
{
    public function test_browser_404_uses_shared_safa_system_design(): void
    {
        $this->get('/definitely-not-a-safa-page?lang=bn')
            ->assertNotFound()
            ->assertSee('পেইজ পাওয়া যায়নি')
            ->assertSee('class="system-page"', false)
            ->assertSee('class="system-card"', false)
            ->assertSee('/safa-web.css', false);
    }

    public function test_api_404_remains_json_instead_of_rendering_browser_markup(): void
    {
        $this->getJson('/api/definitely-not-a-safa-endpoint')
            ->assertNotFound()
            ->assertJson(['status' => 'not_found']);
    }

    public function test_application_relevant_error_pages_share_the_product_stylesheet(): void
    {
        $layout = (string) file_get_contents(resource_path('views/errors/layout.blade.php'));
        $this->assertStringContainsString("url('/safa-web.css')", $layout);
        $this->assertStringContainsString('class="system-page"', $layout);
        $this->assertStringContainsString('class="system-card"', $layout);
        $this->assertStringNotContainsString('fonts.googleapis.com', $layout);
        $this->assertStringNotContainsString('<style>', $layout);

        foreach (['401', '403', '404', '419', '429', '500', '503', '4xx', '5xx'] as $status) {
            $path = resource_path("views/errors/{$status}.blade.php");
            $this->assertFileExists($path);
            $view = (string) file_get_contents($path);
            $this->assertStringContainsString("@extends('errors.layout')", $view);
            $this->assertStringNotContainsString('fonts.googleapis.com', $view);
            $this->assertStringNotContainsString('<style>', $view);
        }
    }

    public function test_first_run_pages_use_the_same_safa_system_components(): void
    {
        foreach (['first_run_database.blade.php', 'first_run_admin.blade.php'] as $file) {
            $view = (string) file_get_contents(resource_path('views/' . $file));
            $this->assertStringContainsString("url('/safa-web.css')", $view);
            $this->assertStringContainsString('class="system-page"', $view);
            $this->assertStringContainsString('class="system-card"', $view);
            $this->assertStringContainsString('class="language-switch"', $view);
            $this->assertStringNotContainsString('<style>', $view);
        }
    }
}
