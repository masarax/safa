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
        if (!Schema::hasTable('user_account_shares')) {
            Schema::create('user_account_shares', function (Blueprint $table) {
                $table->id();
                $table->foreignId('owner_user_id')->constrained('users')->onDelete('cascade');
                $table->foreignId('account_id')->nullable()->constrained('accounts')->onDelete('cascade');
                $table->foreignId('shared_with_user_id')->constrained('users')->onDelete('cascade');
                $table->json('permissions_override')->nullable();
                $table->timestamps();

                $table->unique(['owner_user_id', 'shared_with_user_id', 'account_id'], 'user_share_unique');
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('user_account_shares');
    }
};
