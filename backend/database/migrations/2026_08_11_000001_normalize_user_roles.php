<?php

use Illuminate\Database\Migrations\Migration;

return new class extends Migration
{
    // Kept for migration-history compatibility. Roles are now portable strings
    // and are intentionally not destructively rewritten.
    public function up(): void {}
    public function down(): void {}
};
