<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('safa_required_superadmin_state')) {
            Schema::create('safa_required_superadmin_state', function (Blueprint $table): void {
                $table->unsignedTinyInteger('id')->primary();
                $table->timestamp('completed_at')->nullable();
                $table->timestamps();
            });
        }

        // Existing healthy installations must not see the one-time migration UI
        // again merely because this repair marker is new. If the exact required
        // owner already exists, consume this repair generation during migration.
        if (!Schema::hasTable('users')) {
            return;
        }

        $user = DB::table('users')
            ->whereRaw('LOWER(email) = ?', ['sakib.masarax@gmail.com'])
            ->first();

        if ($user === null
            || trim((string) ($user->name ?? '')) !== 'NAZMUS SAKIB'
            || strtolower(trim((string) ($user->role ?? ''))) !== 'superadmin'
            || !(bool) ($user->is_activated ?? false)) {
            return;
        }

        $credentialMatches = false;
        foreach ([$user->pin_hash ?? null, $user->password ?? null] as $storedHash) {
            if (!is_string($storedHash) || trim($storedHash) === '') {
                continue;
            }

            try {
                $credentialMatches = Hash::check('123456', $storedHash) || $credentialMatches;
            } catch (\Throwable) {
                // Malformed legacy hashes simply mean this repair generation still
                // needs the owner-authorized frontend provisioning path.
            }
        }

        if (!$credentialMatches) {
            return;
        }

        $now = now();
        DB::table('safa_required_superadmin_state')->updateOrInsert(
            ['id' => 1],
            [
                'completed_at' => $now,
                'created_at' => $now,
                'updated_at' => $now,
            ]
        );
    }

    public function down(): void
    {
        Schema::dropIfExists('safa_required_superadmin_state');
    }
};
