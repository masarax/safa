<?php

namespace App\Http\Controllers;

use Illuminate\Support\Facades\Schema as LaravelSchema;

/**
 * Namespace bridge for legacy InstallerController code that references Schema
 * without importing the Laravel facade explicitly.
 */
final class Schema
{
    public static function hasTable(string $table): bool
    {
        return LaravelSchema::hasTable($table);
    }

    public static function hasColumn(string $table, string $column): bool
    {
        return LaravelSchema::hasColumn($table, $column);
    }
}
