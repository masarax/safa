<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\AppVersion;
use App\Models\SystemSetting;
use Illuminate\Support\Str;

class RemoteConfigController extends Controller
{
    /**
     * Read system settings. If empty, create a default row and return values.
     */
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
                ]
            ],
            'settings' => $setting
        ]);
    }

    /**
     * Update system settings in system_settings table.
     */
    public function updateConfig(Request $request)
    {
        $validated = $request->validate([
            'account_id' => 'nullable|integer',
            'app_name' => 'nullable|string|max:255',
            'app_logo_url' => 'nullable|string',
            'app_version' => 'nullable|string|max:50',
            'local_currency' => 'nullable|string|max:10',
            'foreign_currency' => 'nullable|string|max:10',
            'rate_based_mode' => 'nullable|boolean',
            'supplier_rate_enabled' => 'nullable|boolean',
            'wallet_rate_enabled' => 'nullable|boolean',
        ]);

        $setting = SystemSetting::first();
        if (!$setting) {
            $setting = new SystemSetting();
        }

        foreach ([
            'account_id',
            'app_name',
            'app_logo_url',
            'app_version',
            'local_currency',
            'foreign_currency',
            'rate_based_mode',
            'supplier_rate_enabled',
            'wallet_rate_enabled',
        ] as $field) {
            if ($request->has($field) && $request->input($field) !== null) {
                $setting->{$field} = $request->input($field);
            }
        }

        if (!$setting->app_name) $setting->app_name = 'SAFA';
        if (!$setting->app_version) $setting->app_version = '1.0.0';
        if (!$setting->local_currency) $setting->local_currency = 'BDT';
        if (!$setting->foreign_currency) $setting->foreign_currency = 'SAR';

        $setting->save();

        return response()->json([
            'status' => 'success',
            'message' => 'System settings updated successfully',
            'settings' => $setting
        ]);
    }

    /**
     * Upload logo file or base64 image data, store in public/storage/logos/,
     * update app_logo_url in system_settings, and return absolute URL.
     */
    public function uploadLogo(Request $request)
    {
        $destinationPath = public_path('storage/logos');
        if (!file_exists($destinationPath)) {
            mkdir($destinationPath, 0755, true);
        }

        $fileName = null;

        if ($request->hasFile('logo') || $request->hasFile('image') || $request->hasFile('file')) {
            $file = $request->file('logo') ?? $request->file('image') ?? $request->file('file');
            $ext = $file->getClientOriginalExtension() ?: 'png';
            $fileName = 'logo_' . time() . '_' . Str::random(6) . '.' . $ext;
            $file->move($destinationPath, $fileName);
        } elseif ($request->input('logo') || $request->input('image') || $request->input('base64') || $request->input('logo_base64')) {
            $base64String = $request->input('logo') ?? $request->input('image') ?? $request->input('base64') ?? $request->input('logo_base64');
            $ext = 'png';
            if (preg_match('/^data:image\/(\w+);base64,/', $base64String, $type)) {
                $base64String = substr($base64String, strpos($base64String, ',') + 1);
                $ext = strtolower($type[1]);
            }
            $imageData = base64_decode($base64String);
            if ($imageData !== false) {
                $fileName = 'logo_' . time() . '_' . Str::random(6) . '.' . $ext;
                file_put_contents($destinationPath . '/' . $fileName, $imageData);
            } else {
                return response()->json(['status' => 'error', 'message' => 'Invalid base64 image data'], 400);
            }
        } else {
            return response()->json(['status' => 'error', 'message' => 'No logo file or base64 image data provided'], 422);
        }

        $logoUrl = url('storage/logos/' . $fileName);

        $setting = SystemSetting::first();
        if (!$setting) {
            $setting = SystemSetting::create([
                'account_id' => null,
                'app_name' => 'SAFA',
                'app_logo_url' => $logoUrl,
                'app_version' => '1.0.0',
                'local_currency' => 'BDT',
                'foreign_currency' => 'SAR',
                'rate_based_mode' => true,
                'supplier_rate_enabled' => true,
                'wallet_rate_enabled' => true,
            ]);
        } else {
            $setting->app_logo_url = $logoUrl;
            $setting->save();
        }

        return response()->json([
            'status' => 'success',
            'message' => 'Logo uploaded successfully',
            'app_logo_url' => $logoUrl,
            'url' => $logoUrl,
            'settings' => $setting
        ]);
    }

    public function checkVersion(Request $request)
    {
        $validated = $request->validate([
            'version_code' => 'nullable|integer|min:1'
        ]);
        $currentCode = (int) ($validated['version_code'] ?? 1);
        $version = AppVersion::where('platform', 'android')->latest()->first();

        if (!$version) {
            return response()->json([
                'force_update' => false,
                'latest_version_code' => 1,
                'update_url' => null,
                'message' => 'App is up to date.'
            ]);
        }

        $forceUpdate = $currentCode < $version->min_version_code || $version->force_update;

        return response()->json([
            'force_update' => $forceUpdate,
            'latest_version_code' => $version->latest_version_code,
            'update_url' => $version->update_url,
            'message' => $forceUpdate ? 'A mandatory update is required to continue.' : 'Optional update available.'
        ]);
    }
}
