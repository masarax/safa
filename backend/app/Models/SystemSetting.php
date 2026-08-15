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

    /**
     * Return the canonical same-origin path for a generated SAFA logo.
     *
     * Older deployments stored an absolute URL in app_logo_url. Keeping only
     * the generated path at render time prevents a stale APP_URL/domain/scheme
     * from breaking branding after a production move or proxy change.
     */
    public static function uploadedLogoPath(?string $value): ?string
    {
        $raw = trim((string) $value);
        if ($raw === '') return null;

        $path = parse_url($raw, PHP_URL_PATH);
        if (!is_string($path) || $path === '') $path = $raw;
        $path = '/' . ltrim($path, '/');

        return preg_match('#^/storage/logos/logo_[A-Za-z0-9_-]+\.(?:png|jpe?g|gif|webp)$#i', $path)
            ? $path
            : null;
    }

    /**
     * Browser-safe logo source. Generated uploads are deliberately returned as
     * a same-origin relative path; legacy HTTPS custom logos remain supported.
     */
    public function webLogoSource(): string
    {
        if ($path = static::uploadedLogoPath($this->app_logo_url)) return $path;

        $raw = trim((string) $this->app_logo_url);
        if ($raw !== '' && preg_match('#^https://#i', $raw)) return $raw;

        return '/safa-logo.png';
    }

    /** Absolute URL used by Android remote config and other API clients. */
    public function publicLogoUrl(): string
    {
        $source = $this->webLogoSource();
        if (!str_starts_with($source, '/')) return $source;

        if (app()->bound('request')) {
            $request = request();
            return rtrim($request->getSchemeAndHttpHost(), '/') . $source;
        }

        return url($source);
    }
}
