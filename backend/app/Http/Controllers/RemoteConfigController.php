<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\AppVersion;
use App\Models\SystemSetting;
use Illuminate\Support\Str;

class RemoteConfigController extends Controller
{
    private const MAX_LOGO_BYTES = 2_000_000;
    private const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp'];
    private const ALLOWED_MIME_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

    public function getRemoteConfig(Request $request)
    {
        $setting = SystemSetting::first();
        if (!$setting) {
            $setting = SystemSetting::create([
                'account_id' => null,
                'app_name' => 'SAFA',
                'app_logo_url' => url('safa-logo.png'),
                'app_version' => '1.0.0',
                'local_currency' => 'BDT',
                'foreign_currency' => 'SAR',
                'rate_based_mode' => true,
                'supplier_rate_enabled' => true,
                'wallet_rate_enabled' => true,
            ]);
        }

        return response()->json([
            'status' => 'success',
            'config' => [
                'account_id' => $setting->account_id,
                'app_name' => $setting->app_name,
                'app_logo_url' => $setting->app_logo_url ?: url('safa-logo.png'),
                'app_version' => $setting->app_version,
                'local_currency' => $setting->local_currency,
                'foreign_currency' => $setting->foreign_currency,
                'rate_based_mode' => (bool) $setting->rate_based_mode,
                'supplier_rate_enabled' => (bool) $setting->supplier_rate_enabled,
                'wallet_rate_enabled' => (bool) $setting->wallet_rate_enabled,
                'maintenance_mode' => false,
                'default_currency' => $setting->local_currency ?? 'BDT',
                'secondary_currency' => $setting->foreign_currency ?? 'SAR',
                'features' => ['biometric_auth' => true, 'rate_based_transactions' => (bool) $setting->rate_based_mode, 'customer_supplier_ledgers' => true],
                'navigation' => [
                    ['id' => 'dashboard', 'label' => 'Dashboard', 'enabled' => true],
                    ['id' => 'transactions', 'label' => 'Transactions', 'enabled' => true],
                    ['id' => 'ledgers', 'label' => 'Customers & Suppliers', 'enabled' => true],
                    ['id' => 'settings', 'label' => 'Settings', 'enabled' => true],
                ],
            ],
            'settings' => $setting,
        ]);
    }

    public function updateConfig(Request $request)
    {
        $validated = $request->validate([
            'account_id' => 'nullable|integer', 'app_name' => 'nullable|string|max:255', 'app_logo_url' => 'nullable|url|max:2048',
            'app_version' => 'nullable|string|max:50', 'local_currency' => 'nullable|string|max:10', 'foreign_currency' => 'nullable|string|max:10',
            'rate_based_mode' => 'nullable|boolean', 'supplier_rate_enabled' => 'nullable|boolean', 'wallet_rate_enabled' => 'nullable|boolean',
        ]);
        $setting = SystemSetting::first() ?: new SystemSetting();
        foreach (array_keys($validated) as $field) if ($request->input($field) !== null) $setting->{$field} = $validated[$field];
        $setting->app_name = $setting->app_name ?: 'SAFA';
        $setting->app_version = $setting->app_version ?: '1.0.0';
        $setting->local_currency = $setting->local_currency ?: 'BDT';
        $setting->foreign_currency = $setting->foreign_currency ?: 'SAR';
        $setting->save();
        return response()->json(['status' => 'success', 'message' => 'System settings updated successfully', 'settings' => $setting]);
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
            if (!is_string($base64String) || $base64String === '') return response()->json(['status' => 'error', 'message' => 'No logo file or base64 image data provided'], 422);
            $ext = 'png';
            if (preg_match('/^data:image\/(png|jpe?g|gif|webp);base64,/i', $base64String, $type)) {
                $base64String = substr($base64String, strpos($base64String, ',') + 1);
                $ext = strtolower($type[1]);
                if ($ext === 'jpeg') $ext = 'jpg';
            } elseif (str_contains($base64String, 'data:image/')) {
                return response()->json(['status' => 'error', 'message' => 'Unsupported image format.'], 422);
            }
            $base64String = preg_replace('/\s+/', '', $base64String) ?? '';
            if (strlen($base64String) > (int) ceil(self::MAX_LOGO_BYTES * 1.4)) return response()->json(['status' => 'error', 'message' => 'Logo payload is too large.'], 422);
            $imageData = base64_decode($base64String, true);
            if ($imageData === false || strlen($imageData) > self::MAX_LOGO_BYTES) return response()->json(['status' => 'error', 'message' => 'Invalid or oversized image data.'], 422);
            $imageInfo = @getimagesizefromstring($imageData);
            if (!$imageInfo || !in_array(strtolower((string) ($imageInfo['mime'] ?? '')), self::ALLOWED_MIME_TYPES, true)) return response()->json(['status' => 'error', 'message' => 'Invalid raster image data.'], 422);
            $fileName = 'logo_' . time() . '_' . Str::random(16) . '.' . $ext;
            file_put_contents($destinationPath . '/' . $fileName, $imageData, LOCK_EX);
        }

        $logoUrl = url('storage/logos/' . $fileName);
        $targetFile = $destinationPath . '/' . $fileName;
        @copy($targetFile, public_path('safa-logo.png'));

        $setting = SystemSetting::first() ?: SystemSetting::create([
            'account_id' => null, 'app_name' => 'SAFA', 'app_logo_url' => $logoUrl, 'app_version' => '1.0.0',
            'local_currency' => 'BDT', 'foreign_currency' => 'SAR', 'rate_based_mode' => true, 'supplier_rate_enabled' => true, 'wallet_rate_enabled' => true,
        ]);
        if ($setting->app_logo_url !== $logoUrl) { $setting->app_logo_url = $logoUrl; $setting->save(); }

        return response()->json(['status' => 'success', 'message' => 'Logo uploaded successfully', 'app_logo_url' => $logoUrl, 'url' => $logoUrl, 'settings' => $setting]);
    }

    public function checkVersion(Request $request)
    {
        $validated = $request->validate(['version_code' => 'nullable|integer|min:1']);
        $currentCode = (int) ($validated['version_code'] ?? 1);
        $version = AppVersion::where('platform', 'android')->latest()->first();
        if (!$version) return response()->json(['force_update' => false, 'latest_version_code' => 1, 'update_url' => null, 'message' => 'App is up to date.']);
        $forceUpdate = $currentCode < $version->min_version_code || $version->force_update;
        return response()->json(['force_update' => $forceUpdate, 'latest_version_code' => $version->latest_version_code, 'update_url' => $version->update_url, 'message' => $forceUpdate ? 'A mandatory update is required to continue.' : 'Optional update available.']);
    }
}
