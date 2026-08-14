<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Transaction extends Model
{
    use SoftDeletes;

    protected $fillable = [
        'account_id', 'local_id', 'type', 'amount', 'timestamp', 'hash',
        'customer_id', 'supplier_id', 'amount_sar', 'customer_rate', 'supplier_rate',
        'amount_bdt', 'receiver_name', 'receiver_phone', 'receiver_account_type',
        'receiver_account_no', 'wallet_batch_id', 'notes', 'deleted_at'
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'amount_sar' => 'decimal:2',
        'customer_rate' => 'decimal:4',
        'supplier_rate' => 'decimal:4',
        'amount_bdt' => 'decimal:2',
    ];
}
