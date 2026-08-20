<?php

namespace Tests\Feature;

use App\Support\ProductionMigrationSafety;
use Tests\TestCase;

class ProductionMigrationSafetyBypassTest extends TestCase
{
    public function test_compound_column_drop_helper_is_rejected_but_index_replacement_is_allowed(): void
    {
        $source = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        Schema::table('users', function ($table) {
            $table->dropConstrainedForeignId('account_id');
            $table->dropIndex(['email']);
        });
    }
    public function down(): void {}
};
PHP;

        $violations = ProductionMigrationSafety::violations($source);
        $this->assertContains('dropConstrainedForeignId()', $violations);
        $this->assertNotContains('dropIndex()', $violations);
    }

    public function test_concatenated_raw_sql_is_rejected_fail_closed(): void
    {
        $source = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        DB::statement('DROP ' . 'TABLE legacy_rows');
    }
    public function down(): void {}
};
PHP;

        $this->assertContains('dynamic raw SQL', ProductionMigrationSafety::violations($source));
    }

    public function test_variable_and_heredoc_raw_sql_are_rejected_fail_closed(): void
    {
        $variable = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        $sql = 'DELETE FROM audit_logs';
        DB::unprepared($sql);
    }
    public function down(): void {}
};
PHP;
        $heredoc = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        DB::statement(<<<SQL
DROP TABLE legacy_rows
SQL);
    }
    public function down(): void {}
};
PHP;

        $this->assertContains('dynamic raw SQL', ProductionMigrationSafety::violations($variable));
        $this->assertContains('dynamic raw SQL', ProductionMigrationSafety::violations($heredoc));
    }

    public function test_single_literal_additive_raw_sql_remains_allowed(): void
    {
        $source = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        DB::statement('CREATE INDEX customers_phone_idx ON customers (phone)');
    }
    public function down(): void
    {
        DB::statement('DROP INDEX customers_phone_idx');
    }
};
PHP;

        $this->assertSame([], ProductionMigrationSafety::violations($source));
    }
}
