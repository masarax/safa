<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\AppVersion;

class RemoteConfigController extends Controller
{
    public function getRemoteConfig(Request $request)
    {
        return response()->json([
            'status' => 'success',
            'config' => [
                'app_name' => 'SAFA',
                'maintenance_mode' => false,
                'default_currency' => 'BDT',
                'secondary_currency' => 'SAR',
                'features' => [
                    'biometric_auth' => true,
                    'rate_based_transactions' => true,
                    'customer_supplier_ledgers' => true,
                ],
                'navigation' => [
                    ['id' => 'dashboard', 'label' => 'Dashboard', 'enabled' => true],
                    ['id' => 'transactions', 'label' => 'Transactions', 'enabled' => true],
                    ['id' => 'ledgers', 'label' => 'Customers & Suppliers', 'enabled' => true],
                    ['id' => 'settings', 'label' => 'Settings', 'enabled' => true],
                ]
            ]
        ]);
    }

    public function checkVersion(Request $request)
    {
        $currentCode = (int) $request->query('version_code', 1);
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
