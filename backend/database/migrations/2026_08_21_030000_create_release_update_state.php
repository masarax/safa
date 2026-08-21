<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('safa_release_update_state')) {
            Schema::create('safa_release_update_state', function (Blueprint $table): void {
                $table->unsignedTinyInteger('id')->primary();
                $table->string('release_fingerprint', 64)->nullable();
                $table->timestamp('applied_at')->nullable();
                $table->timestamps();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('safa_release_update_state');
    }
};
