<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletLedger extends Model
{
    use SoftDeletes;

    protected $fillable = [
        'account_id',
        'local_id',
        'name',
        'timestamp',
        'deleted_at',
    ];
}

