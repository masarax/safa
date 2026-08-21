<?php

namespace Tests\Feature;

use App\Http\Controllers\TransactionController;
use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class TransactionDefaultStatusTest extends TestCase
{
    use RefreshDatabase;

    public function test_transaction_without_explicit_type_defaults_to_delivered_paid_state(): void
    {
        $user = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Paid Default Account',
            'balance' => 0,
            'owner_user_id' => $user->id,
        ]);

        $request = Request::create('/api/transactions', 'POST', [
            'account_id' => $account->id,
            'local_id' => 218001,
            'amount_sar' => '10.00',
            'amount_bdt' => '325.00',
            'sar_collected' => '10.00',
            'bdt_disbursed' => '325.00',
            'customer_rate' => '32.5000',
            'supplier_rate' => '32.4000',
        ]);
        $request->setUserResolver(fn () => $user);

        $response = app(TransactionController::class)->store($request);

        $this->assertSame(201, $response->getStatusCode());
        $transaction = Transaction::query()->where('local_id', 218001)->firstOrFail();
        $this->assertSame('Delivered', $transaction->type);
    }
}
