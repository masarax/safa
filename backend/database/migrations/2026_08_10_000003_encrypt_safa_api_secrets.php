<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Laravel encrypted values are longer than the legacy varchar(255)
        // column can safely hold. Expand the column before writing ciphertext.
        Schema::table('safa_api_keys', function (Blueprint $table) {
            $table->text('api_secret')->change();
        });

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

        // Restore the original schema only after plaintext restoration.
        Schema::table('safa_api_keys', function (Blueprint $table) {
            $table->string('api_secret', 255)->change();
        });
    }
};
