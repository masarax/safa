<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Supplier extends Model
{
    protected $fillable = ['account_id', 'local_id', 'name', 'phone', 'hash'];
}
