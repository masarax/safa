<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class WalletLedger extends Model
{
    protected $fillable = [
        'account_id',
        'local_id',
        'name',
        'timestamp',
    ];
}
