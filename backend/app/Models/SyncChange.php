<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SyncChange extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'account_id',
        'entity',
        'record_id',
        'operation',
        'snapshot',
        'created_at',
    ];

    protected $casts = [
        'snapshot' => 'array',
        'created_at' => 'datetime',
    ];
}
