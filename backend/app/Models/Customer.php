<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Customer extends Model
{
    use SoftDeletes;

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
