<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Create the database-backed Laravel session table.
     *
     * This migration intentionally lives with Laravel's initial framework
     * migrations so a fresh database always has the table before the app
     * switches to the database session driver.
     */
    public function up(): void
    {
        if (Schema::hasTable('sessions')) {
            return;
        }

        Schema::create('sessions', function (Blueprint $table) {
            $table->string('id')->primary();
            $table->foreignId('user_id')->nullable()->index();
            $table->string('ip_address', 45)->nullable();
            $table->text('user_agent')->nullable();
            $table->longText('payload');
            $table->integer('last_activity')->index();
        });
    }

    /**
     * Drop the session table when rolling back the migration.
     */
    public function down(): void
    {
        Schema::dropIfExists('sessions');
    }
};
