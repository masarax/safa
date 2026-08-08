<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DeviceBinding extends Model
{
    protected $table = 'device_bindings';

    protected $fillable = [
        'user_id',
        'device_uuid',
        'device_model',
        'fingerprint_hash',
        'is_active',
        'bound_at',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'is_active' => 'boolean',
            'bound_at' => 'datetime',
        ];
    }

    /**
     * Get the user that owns the device binding.
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
