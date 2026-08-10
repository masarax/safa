<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::table('safa_api_keys')->orderBy('id')->chunkById(100, function ($rows) {
            foreach ($rows as $row) {
                $secret = (string) ($row->api_secret ?? '');
                if ($secret === '') {
                    continue;
                }

                try {
                    Crypt::decryptString($secret);
                    continue; // Already encrypted.
                } catch (\Throwable $e) {
                    // Legacy plaintext secret; encrypt it below.
                }

                DB::table('safa_api_keys')
                    ->where('id', $row->id)
                    ->update([
                        'api_secret' => Crypt::encryptString($secret),
                        'updated_at' => now(),
                    ]);
            }
        });
    }

    public function down(): void
    {
        DB::table('safa_api_keys')->orderBy('id')->chunkById(100, function ($rows) {
            foreach ($rows as $row) {
                $secret = (string) ($row->api_secret ?? '');
                if ($secret === '') {
                    continue;
                }

                try {
                    $plain = Crypt::decryptString($secret);
                } catch (\Throwable $e) {
                    continue;
                }

                DB::table('safa_api_keys')
                    ->where('id', $row->id)
                    ->update([
                        'api_secret' => $plain,
                        'updated_at' => now(),
                    ]);
            }
        });
    }
};
