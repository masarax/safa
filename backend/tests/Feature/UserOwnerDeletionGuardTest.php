<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class UserOwnerDeletionGuardTest extends TestCase
{
    use RefreshDatabase;

    public function test_owned_business_account_blocks_user_deletion_and_preserves_shares(): void
    {
        $actor = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $owner = User::factory()->create([
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $member = User::factory()->create([
            'role' => User::ROLE_USER,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Protected Business',
            'balance' => 0,
            'owner_user_id' => $owner->id,
        ]);
        $share = UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'account_id' => $account->id,
            'shared_with_user_id' => $member->id,
            'permissions_override' => null,
        ]);

        $response = $this->actingAs($actor)
            ->deleteJson('/app/api/users/' . $owner->id . '?confirmed=true')
            ->assertStatus(409)
            ->assertJsonPath('code', 'ACCOUNT_OWNERSHIP_REQUIRED')
            ->assertJsonPath('account_ids.0', $account->id);

        $this->assertDatabaseHas('users', ['id' => $owner->id]);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'owner_user_id' => $owner->id,
        ]);
        $this->assertDatabaseHas('user_account_shares', [
            'id' => $share->id,
            'owner_user_id' => $owner->id,
            'shared_with_user_id' => $member->id,
            'account_id' => $account->id,
        ]);
    }
}
