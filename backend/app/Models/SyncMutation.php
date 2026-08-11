<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SyncMutation extends Model
{
    protected $fillable = [
        'account_id',
        'mutation_id',
        'entity',
        'local_id',
        'server_id',
        'operation',
        'sync_version',
        'response',
    ];

    protected $casts = [
        'response' => 'array',
        'sync_version' => 'integer',
        'local_id' => 'integer',
        'server_id' => 'integer',
    ];
}
