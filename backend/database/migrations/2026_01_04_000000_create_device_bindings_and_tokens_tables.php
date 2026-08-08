<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('device_bindings', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained('users')->onDelete('cascade');
            $table->string('device_uuid')->index();
            $table->string('device_model')->nullable();
            $table->string('fingerprint_hash');
            $table->boolean('is_active')->default(true);
            $table->timestamp('bound_at')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'device_uuid']);
        });

        Schema::create('auth_sessions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained('users')->onDelete('cascade');
            $table->string('device_uuid')->index();
            $table->text('access_token');
            $table->string('refresh_token')->index();
            $table->string('session_token')->index();
            $table->timestamp('expires_at')->nullable();
            $table->boolean('is_revoked')->default(false);
            $table->timestamps();

            $table->index(['user_id', 'device_uuid']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('auth_sessions');
        Schema::dropIfExists('device_bindings');
    }
};
