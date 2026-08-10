<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Crypt;

class SafaApiKey extends Model
{
    protected $fillable = ['account_id', 'client_name', 'api_key', 'api_secret', 'is_active'];

    protected $casts = [
        'is_active' => 'boolean',
    ];

    /**
     * Read both legacy plaintext secrets and the new encrypted-at-rest format.
     * This keeps authentication working during the short window before the
     * hardening migration is executed on an existing production database.
     */
    public function getApiSecretAttribute($value): string
    {
        $value = (string) $value;
        if ($value === '') return '';

        try {
            return Crypt::decryptString($value);
        } catch (\Throwable $e) {
            return $value;
        }
    }

    public function setApiSecretAttribute($value): void
    {
        $value = trim((string) $value);
        $this->attributes['api_secret'] = $value === '' ? '' : Crypt::encryptString($value);
    }
}
