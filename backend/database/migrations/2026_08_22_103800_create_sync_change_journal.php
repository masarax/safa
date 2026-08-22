<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sync_changes', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('account_id');
            $table->string('entity', 64);
            $table->unsignedBigInteger('entity_id');
            $table->timestamps();

            $table->index(['account_id', 'id'], 'sync_changes_account_cursor_idx');
            $table->index(['account_id', 'entity', 'entity_id', 'id'], 'sync_changes_entity_cursor_idx');
        });

        Schema::create('sync_change_floors', function (Blueprint $table): void {
            $table->unsignedBigInteger('account_id')->primary();
            $table->unsignedBigInteger('floor_cursor')->default(0);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('sync_change_floors');
        Schema::dropIfExists('sync_changes');
    }
};
