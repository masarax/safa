<?php

namespace App\Models;

use App\Models\Concerns\UsesCollisionSafeLocalId;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletLedger extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId;

    protected $fillable = [
        'account_id',
        'local_id',
        'name',
        'timestamp',
        'deleted_at',
    ];
}

