<?php

namespace App\Support;

use RuntimeException;

final class ProductionMigrationSafety
{
    /** @var array<string, string> */
    private const DESTRUCTIVE_METHODS = [
        'rename' => 'rename',
        'renamecolumn' => 'renameColumn',
        'truncate' => 'truncate',
        'delete' => 'delete',
        'forcedelete' => 'forceDelete',
    ];

    /**
     * Index-only replacement is non-data-destructive and is required by an
     * existing production migration before widening encrypted token columns.
     * Other drop* helpers remain fail-closed because they can remove columns,
     * tables or integrity constraints.
     *
     * @var array<int, string>
     */
    private const SAFE_DROP_METHODS = ['dropindex'];

    /** @var array<string, string> */
    private const RAW_SQL_METHODS = [
        'statement' => 'statement',
        'unprepared' => 'unprepared',
        'affectingstatement' => 'affectingStatement',
    ];

    /**
     * @param array<int, string> $migrationNames
     */
    public static function assertPendingMigrationsAreSafe(array $migrationNames): void
    {
        foreach ($migrationNames as $name) {
            if (preg_match('/^[A-Za-z0-9_]+$/', $name) !== 1) {
                throw new RuntimeException('Invalid pending migration name.');
            }

            $path = database_path('migrations/' . $name . '.php');
            if (!is_file($path)) {
                throw new RuntimeException('Pending migration file is missing: ' . $name);
            }

            $source = (string) file_get_contents($path);
            $violations = self::violations($source);
            if ($violations !== []) {
                throw new RuntimeException(
                    'Unsafe production migration blocked: ' . $name . ' [' . implode(', ', $violations) . ']'
                );
            }
        }
    }

    /**
     * Return destructive operations found inside the migration up() method only.
     * Rollback/down() methods are intentionally excluded from the live-update policy.
     *
     * @return array<int, string>
     */
    public static function violations(string $source): array
    {
        $tokens = self::upMethodTokens($source);
        if ($tokens === null) {
            return ['missing up() method'];
        }

        $violations = [];
        $count = count($tokens);

        for ($i = 0; $i < $count; $i++) {
            $token = $tokens[$i];

            if (is_array($token) && $token[0] === T_STRING) {
                $method = strtolower($token[1]);
                $previous = self::previousMeaningfulToken($tokens, $i - 1);
                $isMethodCall = $previous === '->'
                    || $previous === '?->'
                    || $previous === '::';

                if (
                    $isMethodCall
                    && str_starts_with($method, 'drop')
                    && !in_array($method, self::SAFE_DROP_METHODS, true)
                ) {
                    $violations[] = $token[1] . '()';
                } elseif ($isMethodCall && isset(self::DESTRUCTIVE_METHODS[$method])) {
                    $violations[] = self::DESTRUCTIVE_METHODS[$method] . '()';
                }

                if ($isMethodCall && isset(self::RAW_SQL_METHODS[$method])) {
                    foreach (self::rawSqlCallViolations($tokens, $i) as $violation) {
                        $violations[] = $violation;
                    }
                }
            }
        }

        return array_values(array_unique($violations));
    }

    /**
     * Raw SQL is allowed only when the entire first argument is one quoted
     * literal that can be inspected. Variables, concatenation and heredoc/nowdoc
     * are fail-closed because the production updater cannot prove them safe.
     *
     * @param array<int, array{0:int,1:string,2:int}|string> $tokens
     * @return array<int, string>
     */
    private static function rawSqlCallViolations(array $tokens, int $methodIndex): array
    {
        $openIndex = self::nextMeaningfulTokenIndex($tokens, $methodIndex + 1);
        if ($openIndex === null || $tokens[$openIndex] !== '(') {
            return ['dynamic raw SQL'];
        }

        $argumentIndex = self::nextMeaningfulTokenIndex($tokens, $openIndex + 1);
        if ($argumentIndex === null) {
            return ['dynamic raw SQL'];
        }

        $argument = $tokens[$argumentIndex];
        if (!is_array($argument) || $argument[0] !== T_CONSTANT_ENCAPSED_STRING) {
            return ['dynamic raw SQL'];
        }

        $afterArgument = self::nextMeaningfulTokenIndex($tokens, $argumentIndex + 1);
        if ($afterArgument === null || $tokens[$afterArgument] !== ')') {
            return ['dynamic raw SQL'];
        }

        return self::rawSqlLiteralViolations(self::decodeQuotedLiteral($argument[1]));
    }

    /** @return array<int, string> */
    private static function rawSqlLiteralViolations(string $literal): array
    {
        $violations = [];
        if (preg_match('/\bDROP\s+(?:TABLE|COLUMN|DATABASE|INDEX|CONSTRAINT|FOREIGN\s+KEY)\b/i', $literal) === 1) {
            $violations[] = 'raw DROP';
        }
        if (preg_match('/\bTRUNCATE(?:\s+TABLE)?\b/i', $literal) === 1) {
            $violations[] = 'raw TRUNCATE';
        }
        if (preg_match('/\bDELETE\s+FROM\b/i', $literal) === 1) {
            $violations[] = 'raw DELETE';
        }
        if (preg_match('/\bRENAME\s+TABLE\b/i', $literal) === 1) {
            $violations[] = 'raw RENAME TABLE';
        }

        return $violations;
    }

    /**
     * @return array<int, array{0:int,1:string,2:int}|string>|null
     */
    private static function upMethodTokens(string $source): ?array
    {
        $tokens = token_get_all($source);
        $count = count($tokens);

        for ($i = 0; $i < $count; $i++) {
            if (!is_array($tokens[$i]) || $tokens[$i][0] !== T_FUNCTION) {
                continue;
            }

            $nameIndex = self::nextMeaningfulTokenIndex($tokens, $i + 1);
            if ($nameIndex === null) {
                continue;
            }

            // PHP permits an ampersand between `function` and the method name.
            if ($tokens[$nameIndex] === '&') {
                $nameIndex = self::nextMeaningfulTokenIndex($tokens, $nameIndex + 1);
                if ($nameIndex === null) {
                    continue;
                }
            }

            $nameToken = $tokens[$nameIndex];
            if (!is_array($nameToken) || $nameToken[0] !== T_STRING || strtolower($nameToken[1]) !== 'up') {
                continue;
            }

            $openBrace = null;
            for ($j = $nameIndex + 1; $j < $count; $j++) {
                if ($tokens[$j] === '{') {
                    $openBrace = $j;
                    break;
                }
            }

            if ($openBrace === null) {
                return null;
            }

            $body = [];
            $depth = 0;
            for ($j = $openBrace; $j < $count; $j++) {
                $current = $tokens[$j];
                if ($current === '{') {
                    $depth++;
                    if ($depth > 1) {
                        $body[] = $current;
                    }
                    continue;
                }

                if ($current === '}') {
                    $depth--;
                    if ($depth === 0) {
                        return $body;
                    }
                    $body[] = $current;
                    continue;
                }

                if ($depth >= 1) {
                    $body[] = $current;
                }
            }

            return null;
        }

        return null;
    }

    /**
     * @param array<int, array{0:int,1:string,2:int}|string> $tokens
     */
    private static function nextMeaningfulTokenIndex(array $tokens, int $start): ?int
    {
        $count = count($tokens);
        for ($i = $start; $i < $count; $i++) {
            $token = $tokens[$i];
            if (is_array($token) && in_array($token[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
                continue;
            }

            return $i;
        }

        return null;
    }

    /**
     * @param array<int, array{0:int,1:string,2:int}|string> $tokens
     */
    private static function previousMeaningfulToken(array $tokens, int $start): string
    {
        for ($i = $start; $i >= 0; $i--) {
            $token = $tokens[$i];
            if (is_array($token)) {
                if (in_array($token[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
                    continue;
                }

                if (defined('T_NULLSAFE_OBJECT_OPERATOR') && $token[0] === T_NULLSAFE_OBJECT_OPERATOR) {
                    return '?->';
                }
                if ($token[0] === T_OBJECT_OPERATOR) {
                    return '->';
                }
                if ($token[0] === T_DOUBLE_COLON) {
                    return '::';
                }

                return $token[1];
            }

            return $token;
        }

        return '';
    }

    private static function decodeQuotedLiteral(string $literal): string
    {
        if (strlen($literal) < 2) {
            return $literal;
        }

        $quote = $literal[0];
        $body = substr($literal, 1, -1);

        return $quote === "'"
            ? str_replace(["\\\\", "\\'"], ["\\", "'"], $body)
            : stripcslashes($body);
    }
}
