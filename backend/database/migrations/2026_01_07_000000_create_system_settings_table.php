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
        if (!Schema::hasTable('system_settings')) {
            Schema::create('system_settings', function (Blueprint $table) {
                $table->id();
                $table->foreignId('account_id')->nullable()->constrained('accounts')->onDelete('cascade');
                $table->string('app_name')->default('SAFA');
                $table->text('app_logo_url')->nullable();
                $table->string('app_version')->default('1.0.0');
                $table->string('local_currency', 10)->default('BDT');
                $table->string('foreign_currency', 10)->default('SAR');
                $table->boolean('rate_based_mode')->default(true);
                $table->boolean('supplier_rate_enabled')->default(true);
                $table->boolean('wallet_rate_enabled')->default(true);
                $table->timestamps();
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('system_settings');
    }
};
