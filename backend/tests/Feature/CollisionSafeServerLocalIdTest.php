<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\User;
use App\Support\ServerLocalId;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CollisionSafeServerLocalIdTest extends TestCase
{
    use RefreshDatabase;

    public function test_legacy_clock_sized_server_ids_are_replaced_with_distinct_int_compatible_reservations(): void
    {
        $user = User::factory()->create();
        $account = Account::create(['name' => 'Local ID Test', 'balance' => 0, 'owner_user_id' => $user->id]);
        $legacyClockId = 1_787_250_000_000;

        $first = Customer::create([
            'account_id' => $account->id,
            'local_id' => $legacyClockId,
            'name' => 'First',
            'timestamp' => time(),
        ]);
        $second = Customer::create([
            'account_id' => $account->id,
            'local_id' => $legacyClockId,
            'name' => 'Second',
            'timestamp' => time(),
        ]);

        $this->assertNotSame((int) $first->local_id, (int) $second->local_id);
        $this->assertGreaterThan(0, (int) $first->local_id);
        $this->assertLessThanOrEqual(ServerLocalId::MAX_CLIENT_COMPATIBLE_ID, (int) $first->local_id);
        $this->assertDatabaseHas('server_local_id_reservations', ['local_id' => $first->local_id]);
        $this->assertDatabaseHas('server_local_id_reservations', ['local_id' => $second->local_id]);
    }

    public function test_android_compatible_local_ids_are_preserved(): void
    {
        $user = User::factory()->create();
        $account = Account::create(['name' => 'Android ID Test', 'balance' => 0, 'owner_user_id' => $user->id]);
        $clientLocalId = 987_654_321;

        $supplier = Supplier::create([
            'account_id' => $account->id,
            'local_id' => $clientLocalId,
            'name' => 'Client Supplier',
            'timestamp' => time(),
        ]);

        $this->assertSame($clientLocalId, (int) $supplier->local_id);
        $this->assertDatabaseMissing('server_local_id_reservations', ['local_id' => $clientLocalId]);
    }
}
