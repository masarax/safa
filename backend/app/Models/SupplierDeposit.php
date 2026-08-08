<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class SupplierDeposit extends Model
{
    use SoftDeletes;

    protected $fillable = [
        'account_id',
        'local_id',
        'supplier_id',
        'amount_sar',
        'rate',
        'amount_bdt',
        'paid_bdt',
        'transaction_type',
        'notes',
        'timestamp',
        'deleted_at',
    ];
}

