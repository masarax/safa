<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (Schema::hasTable('safa_installation_state')) {
            return;
        }

        Schema::create('safa_installation_state', function (Blueprint $table): void {
            $table->unsignedTinyInteger('id')->primary();
            $table->string('bootstrap_claim_hash', 64);
            $table->timestamp('database_initialized_at');
            $table->timestamp('completed_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('safa_installation_state');
    }
};
