<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\ReleaseUpdateState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class ReleaseUpdateGateTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
    }

    public function test_connected_database_shows_clean_update_gate_and_preserves_existing_data(): void
    {
        $passwordHash = Hash::make('existing-password');
        $pinHash = Hash::make('654321');
        $existingUser = User::factory()->create([
            'name' => 'Existing Owner',
            'mobile' => '0500000210',
            'email' => 'existing-210@safa.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::query()->create([
            'owner_user_id' => $existingUser->id,
            'name' => 'Existing Account',
            'balance' => '777.50',
        ]);
        $customer = Customer::query()->create([
            'account_id' => $account->id,
            'local_id' => 210,
            'name' => 'Existing Customer',
            'phone' => '0500000310',
        ]);

        $this->assertTrue(ReleaseUpdateState::required());
        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/update?lang=bn')
            ->assertOk()
            ->assertSee('সিস্টেম আপডেট প্রস্তুত')
            ->assertSee('আপডেট চালান')
            ->assertDontSee('Pending migration')
            ->assertDontSee('setup code')
            ->assertDontSee('storage/app/private');

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $required = User::query()->where('email', 'sakib.masarax@gmail.com')->firstOrFail();
        $this->assertSame('NAZMUS SAKIB', $required->name);
        $this->assertTrue($required->isSuperAdmin());
        $this->assertTrue((bool) $required->is_activated);
        $this->assertTrue(CredentialVerifier::verify('123456', [
            $required->pin_hash,
            $required->password,
        ]));
        $this->assertSame(1, User::query()->where('email', 'sakib.masarax@gmail.com')->count());

        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'name' => 'Existing Account',
            'balance' => '777.50',
        ]);
        $this->assertDatabaseHas('customers', [
            'id' => $customer->id,
            'account_id' => $account->id,
            'name' => 'Existing Customer',
        ]);
        $existingUser->refresh();
        $this->assertSame($passwordHash, $existingUser->password);
        $this->assertSame($pinHash, $existingUser->pin_hash);

        $this->assertFalse(ReleaseUpdateState::required());
        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
        $this->get('/data-migration')->assertNotFound();
        $this->get('/setup')->assertNotFound();
    }

    public function test_later_release_update_does_not_reset_existing_required_superadmin_credentials(): void
    {
        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $required = User::query()->where('email', 'sakib.masarax@gmail.com')->firstOrFail();
        $changedPassword = Hash::make('changed-password');
        $changedPin = Hash::make('777777');
        $required->forceFill([
            'name' => 'Owner Changed Name',
            'password' => $changedPassword,
            'pin_hash' => $changedPin,
        ])->save();

        DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->update([
            'release_fingerprint' => str_repeat('0', 64),
            'updated_at' => now(),
        ]);
        $this->assertTrue(ReleaseUpdateState::required());
        $this->get('/')->assertRedirect(route('system.update.show'));

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $required->refresh();
        $this->assertSame('Owner Changed Name', $required->name);
        $this->assertSame($changedPassword, $required->password);
        $this->assertSame($changedPin, $required->pin_hash);
        $this->assertSame(1, User::query()->where('email', 'sakib.masarax@gmail.com')->count());
        $this->assertFalse(ReleaseUpdateState::required());
        $this->get('/update')->assertRedirect(route('safa.login'));
    }

    public function test_api_reports_update_required_without_internal_migration_details(): void
    {
        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'update_required',
                'update_path' => '/update',
            ])
            ->assertJsonMissingPath('pending_count')
            ->assertJsonMissingPath('migration_path')
            ->assertJsonMissingPath('setup_path');
    }
}
