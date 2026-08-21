<?php

namespace Tests\Feature;

use App\Http\Controllers\AccountContextController;
use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class OwnAccountSharingContractTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $name, string $mobile): User
    {
        return User::create([
            'name' => $name,
            'email' => strtolower(str_replace(' ', '-', $name)) . '@safa.local',
            'mobile' => $mobile,
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);
    }

    public function test_share_provisions_authenticated_users_owned_account_without_account_id(): void
    {
        $actor = $this->user('Share Actor', '0536308971');
        $target = $this->user('Share Target', '0536308972');
        $this->assertSame(0, Account::query()->where('owner_user_id', $actor->id)->count());

        $request = Request::create('/api/accounts/share', 'POST', [
            'mobile' => $target->mobile,
            'permissions_override' => [
                'can_view_customers' => true,
                'can_view_suppliers' => false,
            ],
        ]);
        $request->setUserResolver(fn () => $actor);

        $response = app(AccountContextController::class)->share($request);
        $payload = $response->getData(true);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame('success', $payload['status']);

        $owned = Account::query()->where('owner_user_id', $actor->id)->sole();
        $this->assertDatabaseHas('user_account_shares', [
            'owner_user_id' => $actor->id,
            'shared_with_user_id' => $target->id,
            'account_id' => $owned->id,
        ]);
    }

    public function test_client_supplied_or_active_foreign_account_cannot_change_which_account_is_shared(): void
    {
        $actor = $this->user('Own Account Actor', '0536308973');
        $foreignOwner = $this->user('Foreign Owner', '0536308974');
        $target = $this->user('Foreign Share Target', '0536308975');

        $owned = Account::create([
            'name' => 'Actors Own Account',
            'owner_user_id' => $actor->id,
            'balance' => 0,
        ]);
        $foreign = Account::create([
            'name' => 'Foreign Shared Account',
            'owner_user_id' => $foreignOwner->id,
            'balance' => 0,
        ]);
        UserAccountShare::create([
            'owner_user_id' => $foreignOwner->id,
            'shared_with_user_id' => $actor->id,
            'account_id' => $foreign->id,
            'permissions_override' => ['can_view_customers' => true],
        ]);

        $request = Request::create('/api/accounts/share', 'POST', [
            'mobile' => $target->mobile,
            // Backward-compatible clients may still send this field. The server
            // must ignore it and derive the actor's owned account instead.
            'account_id' => $foreign->id,
        ]);
        $request->headers->set('X-SAFA-ACCOUNT-ID', (string) $foreign->id);
        $request->setUserResolver(fn () => $actor);

        $response = app(AccountContextController::class)->share($request);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertDatabaseHas('user_account_shares', [
            'owner_user_id' => $actor->id,
            'shared_with_user_id' => $target->id,
            'account_id' => $owned->id,
        ]);
        $this->assertDatabaseMissing('user_account_shares', [
            'owner_user_id' => $foreignOwner->id,
            'shared_with_user_id' => $target->id,
            'account_id' => $foreign->id,
        ]);
    }
}
