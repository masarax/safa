<?php

namespace Tests\Feature;

use App\Http\Controllers\RemoteConfigController;
use App\Http\Middleware\CheckApiSecurityKey;
use App\Models\Account;
use App\Models\SystemSetting;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use ReflectionMethod;
use Tests\TestCase;

class Issue220PersistenceTest extends TestCase
{
    use RefreshDatabase;

    public function test_canonical_android_client_id_does_not_depend_on_api_key_database_seed(): void
    {
        config([
            'safa.mobile_client_key' => '',
            'safa.canonical_mobile_client_key' => 'safa_key_public_client_id',
        ]);

        $request = Request::create('/api/sync/up', 'POST');
        $request->headers->set('X-SAFA-API-KEY', 'safa_key_public_client_id');
        $request->headers->set('X-SAFA-CLIENT', 'android');

        $response = (new CheckApiSecurityKey())->handle(
            $request,
            fn () => response()->json(['status' => 'ok'])
        );

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame('ok', $response->getData(true)['status']);
    }

    public function test_remote_settings_are_persisted_per_business_account(): void
    {
        SystemSetting::create([
            'account_id' => null,
            'app_name' => 'Legacy SAFA',
            'app_logo_url' => '/safa-logo.png',
            'app_version' => '1.1.0',
            'local_currency' => 'BDT',
            'foreign_currency' => 'SAR',
            'rate_based_mode' => true,
            'supplier_rate_enabled' => true,
            'wallet_rate_enabled' => true,
        ]);

        $accountA = Account::create(['name' => 'Account A', 'balance' => 0]);
        $accountB = Account::create(['name' => 'Account B', 'balance' => 0]);

        $method = new ReflectionMethod(RemoteConfigController::class, 'settingForAccount');
        $method->setAccessible(true);
        $controller = app(RemoteConfigController::class);

        /** @var SystemSetting $settingA */
        $settingA = $method->invoke($controller, (int) $accountA->id);
        /** @var SystemSetting $settingB */
        $settingB = $method->invoke($controller, (int) $accountB->id);

        $this->assertSame((int) $accountA->id, (int) $settingA->account_id);
        $this->assertSame((int) $accountB->id, (int) $settingB->account_id);
        $this->assertNotSame((int) $settingA->id, (int) $settingB->id);

        $settingA->app_logo_url = '/storage/logos/logo_account_a.webp';
        $settingA->saveOrFail();

        $this->assertDatabaseHas('system_settings', [
            'account_id' => $accountA->id,
            'app_logo_url' => '/storage/logos/logo_account_a.webp',
        ]);
        $this->assertDatabaseHas('system_settings', [
            'account_id' => $accountB->id,
            'app_logo_url' => '/safa-logo.png',
        ]);
    }
}
