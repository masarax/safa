<?php

namespace App\Models;

use App\Models\Concerns\UsesCollisionSafeLocalId;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletBatch extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId;

    protected $fillable = [
        'account_id', 'local_id', 'ledger_id', 'rate', 'initial_bdt', 'remaining_bdt',
        'supplier_id', 'supplier_deposit_id', 'notes', 'timestamp', 'deleted_at',
    ];

    protected $casts = [
        'rate' => 'decimal:4',
        'initial_bdt' => 'decimal:2',
        'remaining_bdt' => 'decimal:2',
    ];
}
