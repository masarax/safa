<?php

use App\Models\Account;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * Register the production API credentials in the database so the HMAC
     * middleware uses the same credentials as the Laravel environment.
     */
    public function up(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        // Tests must be reproducible without requiring real production secrets.
        if ($apiKey === '' || $apiSecret === '') {
            if (app()->environment('testing')) {
                $apiKey = 'safa_testing_key';
                $apiSecret = 'safa_testing_secret';
            } else {
                throw new RuntimeException(
                    'SAFA_API_KEY and SAFA_API_SECRET must be configured in the backend .env before running migrations.'
                );
            }
        }

        $account = Account::firstOrCreate(['name' => 'SAFA Account']);

        DB::table('safa_api_keys')->updateOrInsert(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => $account->id,
                'api_key' => $apiKey,
                'api_secret' => $apiSecret,
                'is_active' => true,
                'updated_at' => now(),
                'created_at' => now(),
            ]
        );

        DB::table('safa_api_keys')
            ->where('api_key', $apiKey)
            ->where('client_name', '!=', 'SAFA Mobile Client')
            ->update([
                'is_active' => false,
                'updated_at' => now(),
            ]);
    }

    public function down(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        if ($apiKey === '' && app()->environment('testing')) {
            $apiKey = 'safa_testing_key';
        }

        if ($apiKey !== '') {
            DB::table('safa_api_keys')
                ->where('client_name', 'SAFA Mobile Client')
                ->where('api_key', $apiKey)
                ->delete();
        }
    }
};
