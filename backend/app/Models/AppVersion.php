<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AppVersion extends Model
{
    protected $fillable = ['platform', 'min_version_code', 'latest_version_code', 'force_update', 'update_url'];
}
