<?php

namespace App\Models;

use App\Models\Concerns\RecordsSyncChanges;
use App\Models\Concerns\UsesCollisionSafeLocalId;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Supplier extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId, RecordsSyncChanges;

    protected $fillable = [
        'account_id',
        'local_id',
        'name',
        'phone',
        'avatar_color',
        'avatar_emoji',
        'address',
        'hash',
        'timestamp',
        'deleted_at',
    ];
}
