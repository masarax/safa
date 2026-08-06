<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Transaction extends Model
{
    protected $fillable = [
        'account_id', 'local_id', 'type', 'amount', 'timestamp', 'hash',
        'customer_id', 'supplier_id', 'amount_sar', 'customer_rate', 'supplier_rate',
        'amount_bdt', 'receiver_name', 'receiver_phone', 'receiver_account_type',
        'receiver_account_no', 'wallet_batch_id', 'notes'
    ];
}
