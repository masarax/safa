<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('safa_frontend_migration_state')) {
            Schema::create('safa_frontend_migration_state', function (Blueprint $table): void {
                $table->unsignedTinyInteger('id')->primary();
                $table->timestamp('completed_at')->nullable();
                $table->timestamps();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('safa_frontend_migration_state');
    }
};
