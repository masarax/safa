<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebAssetPipelineContractTest extends TestCase
{
    public function test_checked_in_public_assets_are_the_only_production_frontend_pipeline(): void
    {
        $root = base_path('..');
        $composer = (string) file_get_contents(base_path('composer.json'));
        $shell = (string) file_get_contents(resource_path('views/safa/app.blade.php'));

        $this->assertFileDoesNotExist(base_path('package.json'));
        $this->assertFileDoesNotExist(base_path('vite.config.js'));
        $this->assertFileDoesNotExist(resource_path('css/app.css'));
        $this->assertFileDoesNotExist(resource_path('js/app.js'));
        $this->assertStringNotContainsString('npm ', $composer);
        $this->assertStringNotContainsString('npx ', $composer);
        $this->assertStringNotContainsString('@vite', $shell);

        foreach (['safa-web.css', 'safa-web.js', 'safa-web-product.js'] as $asset) {
            $this->assertFileExists(public_path($asset));
            $this->assertStringContainsString("/$asset", $shell);
        }

        $this->assertFileExists($root . '/docs/web-assets.md');
    }
}
