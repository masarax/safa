<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Transaction extends Model
{
    protected $fillable = ['account_id', 'local_id', 'type', 'amount', 'timestamp', 'hash'];
}
