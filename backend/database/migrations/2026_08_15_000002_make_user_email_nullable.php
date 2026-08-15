<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table): void {
            // Mobile is a canonical standalone login identifier. Keep the
            // existing unique email index for non-null values while allowing
            // legitimate mobile-only accounts.
            $table->string('email')->nullable()->change();
        });
    }

    public function down(): void
    {
        // Do not make the column NOT NULL automatically: production may now
        // contain valid mobile-only users and a destructive rollback would fail.
    }
};
