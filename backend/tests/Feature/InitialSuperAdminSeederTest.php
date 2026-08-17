<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Database\Seeders\InitialSuperAdminSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class InitialSuperAdminSeederTest extends TestCase
{
    use RefreshDatabase;

    public function test_interactive_seed_prompts_for_and_creates_first_superadmin(): void
    {
        $this->artisan('db:seed', [
            '--class' => InitialSuperAdminSeeder::class,
            '--force' => true,
        ])
            ->expectsQuestion('Super Admin name', 'Primary Admin')
            ->expectsQuestion('Mobile number', '+966536308965')
            ->expectsQuestion('Email address', 'ADMIN@EXAMPLE.TEST')
            ->expectsQuestion('6-digit PIN', '654321')
            ->expectsQuestion('Confirm 6-digit PIN', '654321')
            ->expectsOutputToContain('Initial Super Admin created successfully.')
            ->assertExitCode(0);

        $admin = User::query()->where('role', User::ROLE_SUPERADMIN)->firstOrFail();
        $this->assertSame('Primary Admin', $admin->name);
        $this->assertSame('admin@example.test', $admin->email);
        $this->assertTrue((bool) $admin->is_activated);
        $this->assertTrue(Hash::check('654321', (string) $admin->pin_hash));
        $this->assertTrue(Hash::check('654321', (string) $admin->password));
    }

    public function test_programmatic_database_seed_never_creates_a_default_superadmin(): void
    {
        $this->seed(DatabaseSeeder::class);

        $this->assertFalse(User::query()->where('role', User::ROLE_SUPERADMIN)->exists());
        $this->assertDatabaseHas('app_versions', [
            'platform' => 'android',
            'min_version_code' => 2,
            'latest_version_code' => 2,
        ]);
    }

    public function test_reseeding_does_not_replace_existing_superadmin_credentials(): void
    {
        $admin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
            'password' => Hash::make('112233'),
            'pin_hash' => Hash::make('112233'),
        ]);
        $passwordHash = $admin->password;
        $pinHash = $admin->pin_hash;

        $this->seed(DatabaseSeeder::class);

        $admin->refresh();
        $this->assertSame($passwordHash, $admin->password);
        $this->assertSame($pinHash, $admin->pin_hash);
        $this->assertTrue(Hash::check('112233', (string) $admin->pin_hash));
    }
}
