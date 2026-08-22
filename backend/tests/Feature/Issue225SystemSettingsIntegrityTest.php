<?php

namespace Tests\Feature;

use App\Http\Controllers\RemoteConfigController;
use App\Models\Account;
use App\Models\SystemSetting;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use ReflectionMethod;
use Tests\TestCase;

class Issue225SystemSettingsIntegrityTest extends TestCase
{
    use RefreshDatabase;

    public function test_scoped_settings_initialization_is_idempotent_and_reuses_one_row(): void
    {
        SystemSetting::create([
            'account_id' => null,
            'app_name' => 'Legacy Brand',
            'app_logo_url' => '/legacy.png',
            'app_version' => '2.0.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ]);
        $account = Account::create(['name' => 'Business', 'balance' => 0]);

        $method = new ReflectionMethod(RemoteConfigController::class, 'settingForAccount');
        $method->setAccessible(true);
        $controller = app(RemoteConfigController::class);

        /** @var SystemSetting $first */
        $first = $method->invoke($controller, (int) $account->id);
        /** @var SystemSetting $second */
        $second = $method->invoke($controller, (int) $account->id);

        $this->assertSame((int) $first->id, (int) $second->id);
        $this->assertSame('Legacy Brand', $first->app_name);
        $this->assertSame(1, SystemSetting::where('account_id', $account->id)->count());
    }

    public function test_database_rejects_a_second_scoped_settings_row_for_same_account(): void
    {
        $account = Account::create(['name' => 'Business', 'balance' => 0]);
        $payload = [
            'account_id' => $account->id,
            'app_name' => 'SAFA',
            'app_version' => '1.0.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ];

        SystemSetting::create($payload);

        $this->expectException(QueryException::class);
        SystemSetting::create($payload);
    }

    public function test_nullable_legacy_global_rows_remain_backward_compatible(): void
    {
        $base = [
            'account_id' => null,
            'app_version' => '1.0.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ];

        SystemSetting::create($base + ['app_name' => 'Legacy A']);
        SystemSetting::create($base + ['app_name' => 'Legacy B']);

        $this->assertSame(2, SystemSetting::whereNull('account_id')->count());
    }
}
