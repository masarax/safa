<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class WalletBatch extends Model
{
    protected $fillable = [
        'account_id',
        'local_id',
        'ledger_id',
        'rate',
        'initial_bdt',
        'remaining_bdt',
        'supplier_id',
        'supplier_deposit_id',
        'notes',
        'timestamp',
    ];
}
