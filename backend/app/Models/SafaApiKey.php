<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SafaApiKey extends Model
{
    protected $fillable = ['account_id', 'client_name', 'api_key', 'api_secret', 'is_active'];

    protected function casts(): array
    {
        return [
            'api_secret' => 'encrypted',
            'is_active' => 'boolean',
        ];
    }
}
