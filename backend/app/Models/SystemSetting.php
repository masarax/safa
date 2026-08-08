<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SystemSetting extends Model
{
    protected $table = 'system_settings';

    protected $fillable = [
        'account_id',
        'app_name',
        'app_logo_url',
        'app_version',
        'local_currency',
        'foreign_currency',
        'rate_based_mode',
        'supplier_rate_enabled',
        'wallet_rate_enabled',
    ];

    protected $casts = [
        'rate_based_mode' => 'boolean',
        'supplier_rate_enabled' => 'boolean',
        'wallet_rate_enabled' => 'boolean',
    ];
}
