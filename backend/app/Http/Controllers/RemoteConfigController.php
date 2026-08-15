<?php

namespace App\Http\Controllers;

use App\Models\AppVersion;
use App\Models\SystemSetting;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class RemoteConfigController extends Controller
{
    private const MAX_LOGO_BYTES = 2_000_000;
    private const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp'];
    private const ALLOWED_MIME_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

    private function setting(): SystemSetting
    {
        return SystemSetting::first() ?: SystemSetting::create([
            'account_id' => null,
            'app_name' => 'SAFA',
            'app_logo_url' => '/safa-logo.png',
            'app_version' => '1.0.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ]);
    }

    private function publicSettings(SystemSetting $setting, ?string $captainName = null): array
    {
        $settings = $setting->toArray();
        $settings['app_logo_url'] = $setting->publicLogoUrl();
        // Read-only compatibility field for installed Android clients. Identity
        // changes are handled by WebSettingsController / user management, never
        // by the system-brand configuration endpoint.
        $settings['captain_name'] = $captainName;
        return $settings;
    }

    public function getRemoteConfig(Request $request)
    {
        $setting = $this->setting();
        $captainName = $request->user()?->name;

        return response()->json([
            'status' => 'success',
            'config' => [
                'account_id' => $setting->account_id,
                'app_name' => $setting->app_name,
                'captain_name' => $captainName,
                'app_logo_url' => $setting->publicLogoUrl(),
                'app_version' => $setting->app_version,
                'local_currency' => $setting->local_currency,
                'foreign_currency' => $setting->foreign_currency,
                'rate_based_mode' => (bool) $setting->rate_based_mode,
                'supplier_rate_enabled' => (bool) $setting->supplier_rate_enabled,
                'wallet_rate_enabled' => (bool) $setting->wallet_rate_enabled,
                'maintenance_mode' => false,
                'default_currency' => $setting->local_currency ?? 'BDT',
                'secondary_currency' => $setting->foreign_currency ?? 'SAR',
                'features' => [
                    'biometric_auth' => true,
                    'rate_based_transactions' => (bool) $setting->rate_based_mode,
                    'customer_supplier_ledgers' => true,
                ],
                'navigation' => [
                    ['id' => 'dashboard', 'label' => 'Dashboard', 'enabled' => true],
                    ['id' => 'transactions', 'label' => 'Transactions', 'enabled' => true],
                    ['id' => 'ledgers', 'label' => 'Customers & Suppliers', 'enabled' => true],
                    ['id' => 'settings', 'label' => 'Settings', 'enabled' => true],
                ],
            ],
            'settings' => $this->publicSettings($setting, $captainName),
        ]);
    }

    public function updateConfig(Request $request)
    {
        // User identity is never a brand/system setting. Reject legacy callers
        // explicitly instead of silently accepting and ignoring the field.
        if ($request->has('captain_name')) {
            return response()->json([
                'status' => 'error',
                'message' => 'User name must be changed from My Account settings.',
                'errors' => ['captain_name' => ['User identity is not part of Brand & Business Configuration.']],
            ], 422);
        }

        $validated = $request->validate([
            'account_id' => 'nullable|integer',
            'app_name' => 'nullable|string|max:255',
            'app_logo_url' => 'nullable|url|max:2048',
            'app_version' => 'nullable|string|max:50',
            'local_currency' => 'nullable|string|max:10',
            'foreign_currency' => 'nullable|string|max:10',
            'rate_based_mode' => 'nullable|boolean',
            'supplier_rate_enabled' => 'nullable|boolean',
            'wallet_rate_enabled' => 'nullable|boolean',
        ]);

        $user = $request->user();
        if (array_key_exists('app_version', $validated) && !$user?->isSuperAdmin()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Only Super Admin can change application version metadata.',
            ], 403);
        }

        $setting = $this->setting();
        foreach ($validated as $field => $value) {
            if ($value === null) continue;

            if ($field === 'app_logo_url') {
                $setting->app_logo_url = SystemSetting::uploadedLogoPath((string) $value) ?: (string) $value;
                continue;
            }

            if (in_array($field, ['local_currency', 'foreign_currency'], true)) {
                $setting->{$field} = strtoupper(trim((string) $value));
                continue;
            }

            if (in_array($field, ['app_name', 'app_version'], true)) {
                $setting->{$field} = trim((string) $value);
                continue;
            }

            $setting->{$field} = $value;
        }

        $setting->app_name = $setting->app_name ?: 'SAFA';
        $setting->app_version = $setting->app_version ?: '1.0.0';
        $setting->local_currency = $setting->local_currency ?: 'BDT';
        $setting->foreign_currency = $setting->foreign_currency ?: 'SAR';
        $setting->save();

        return response()->json([
            'status' => 'success',
            'message' => 'System settings updated successfully',
            'settings' => $this->publicSettings($setting, $user?->fresh()?->name),
        ]);
    }

    /** Store only raster images. SVG is deliberately rejected to prevent active-content XSS. */
    public function uploadLogo(Request $request)
    {
        $destinationPath = public_path('storage/logos');
        if (!is_dir($destinationPath) && !mkdir($destinationPath, 0755, true) && !is_dir($destinationPath)) {
            return response()->json(['status' => 'error', 'message' => 'Unable to create logo storage directory.'], 500);
        }

        $fileName = null;
        $file = $request->file('logo') ?? $request->file('image') ?? $request->file('file');

        if ($file) {
            if (!$file->isValid() || $file->getSize() > self::MAX_LOGO_BYTES) {
                return response()->json(['status' => 'error', 'message' => 'Logo must be a valid image no larger than 2 MB.'], 422);
            }
            $mime = strtolower((string) $file->getMimeType());
            $ext = strtolower((string) $file->guessExtension());
            if (!in_array($mime, self::ALLOWED_MIME_TYPES, true) || !in_array($ext, self::ALLOWED_EXTENSIONS, true)) {
                return response()->json(['status' => 'error', 'message' => 'Invalid logo file type. PNG, JPG, GIF and WEBP are permitted.'], 422);
            }
            $fileName = 'logo_' . time() . '_' . Str::random(16) . '.' . $ext;
            $file->move($destinationPath, $fileName);
        } else {
            $base64String = $request->input('logo') ?? $request->input('image') ?? $request->input('base64') ?? $request->input('logo_base64');
            if (!is_string($base64String) || $base64String === '') {
                return response()->json(['status' => 'error', 'message' => 'No logo file or base64 image data provided'], 422);
            }

            $ext = 'png';
            if (preg_match('/^data:image\/(png|jpe?g|gif|webp);base64,/i', $base64String, $type)) {
                $base64String = substr($base64String, strpos($base64String, ',') + 1);
                $ext = strtolower($type[1]);
                if ($ext === 'jpeg') $ext = 'jpg';
            } elseif (str_contains($base64String, 'data:image/')) {
                return response()->json(['status' => 'error', 'message' => 'Unsupported image format.'], 422);
            }

            $base64String = preg_replace('/\s+/', '', $base64String) ?? '';
            if (strlen($base64String) > (int) ceil(self::MAX_LOGO_BYTES * 1.4)) {
                return response()->json(['status' => 'error', 'message' => 'Logo payload is too large.'], 422);
            }

            $imageData = base64_decode($base64String, true);
            if ($imageData === false || strlen($imageData) > self::MAX_LOGO_BYTES) {
                return response()->json(['status' => 'error', 'message' => 'Invalid or oversized image data.'], 422);
            }

            $imageInfo = @getimagesizefromstring($imageData);
            if (!$imageInfo || !in_array(strtolower((string) ($imageInfo['mime'] ?? '')), self::ALLOWED_MIME_TYPES, true)) {
                return response()->json(['status' => 'error', 'message' => 'Invalid raster image data.'], 422);
            }

            $fileName = 'logo_' . time() . '_' . Str::random(16) . '.' . $ext;
            if (file_put_contents($destinationPath . '/' . $fileName, $imageData, LOCK_EX) === false) {
                return response()->json(['status' => 'error', 'message' => 'Unable to store logo image.'], 500);
            }
        }

        $logoPath = '/storage/logos/' . $fileName;
        $setting = $this->setting();
        $setting->app_logo_url = $logoPath;
        $setting->save();

        return response()->json([
            'status' => 'success',
            'message' => 'Logo uploaded successfully',
            'app_logo_path' => $logoPath,
            'app_logo_url' => $setting->publicLogoUrl(),
            'url' => $setting->publicLogoUrl(),
            'settings' => $this->publicSettings($setting, $request->user()?->name),
        ]);
    }

    public function checkVersion(Request $request)
    {
        $validated = $request->validate(['version_code' => 'nullable|integer|min:1']);
        $currentCode = (int) ($validated['version_code'] ?? 1);
        $version = AppVersion::where('platform', 'android')->latest()->first();

        if (!$version) {
            return response()->json([
                'force_update' => false,
                'update_available' => false,
                'latest_version_code' => 1,
                'update_url' => null,
                'message' => 'App is up to date.',
            ]);
        }

        $latestCode = (int) $version->latest_version_code;
        $minimumCode = (int) $version->min_version_code;
        $updateAvailable = $currentCode < $latestCode;
        $forceUpdate = $updateAvailable && ($currentCode < $minimumCode || (bool) $version->force_update);

        $message = match (true) {
            $forceUpdate => 'A mandatory update is required to continue.',
            $updateAvailable => 'An optional update is available.',
            default => 'App is up to date.',
        };

        return response()->json([
            'force_update' => $forceUpdate,
            'update_available' => $updateAvailable,
            'latest_version_code' => $latestCode,
            'update_url' => $version->update_url,
            'message' => $message,
        ]);
    }
}
