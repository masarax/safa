<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('server_local_id_reservations')) {
            Schema::create('server_local_id_reservations', function (Blueprint $table) {
                $table->id();
                $table->unsignedInteger('local_id')->unique();
                $table->timestamp('created_at')->useCurrent();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('server_local_id_reservations');
    }
};
