<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletBatch extends Model
{
    use SoftDeletes;

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
        'deleted_at',
    ];
}

