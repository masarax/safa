<?php

declare(strict_types=1);

$root = dirname(__DIR__) . '/backend';
$scanRoots = ['app', 'config', 'database/migrations', 'database/seeders', 'routes'];
$dangerousCalls = [
    'assert',
    'create_function',
    'eval',
    'exec',
    'passthru',
    'popen',
    'proc_open',
    'shell_exec',
    'system',
    'unserialize',
];
$dangerousCalls = array_fill_keys($dangerousCalls, true);
$findings = [];

foreach ($scanRoots as $relativeRoot) {
    $directory = $root . '/' . $relativeRoot;
    if (!is_dir($directory)) {
        $findings[] = "$relativeRoot: scan root is missing";
        continue;
    }

    $iterator = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($directory, FilesystemIterator::SKIP_DOTS)
    );
    foreach ($iterator as $fileInfo) {
        if (!$fileInfo->isFile() || strtolower($fileInfo->getExtension()) !== 'php') {
            continue;
        }

        $path = $fileInfo->getPathname();
        $relative = substr($path, strlen($root) + 1);
        $source = (string) file_get_contents($path);
        $tokens = token_get_all($source);

        foreach ($tokens as $index => $token) {
            if (!is_array($token)) {
                continue;
            }

            [$id, $text, $line] = $token;
            if ($id === T_EVAL) {
                $findings[] = "$relative:$line: eval is forbidden in production PHP";
                continue;
            }
            if ($id !== T_STRING) {
                continue;
            }

            $name = strtolower($text);
            if (!isset($dangerousCalls[$name])) {
                continue;
            }

            // Ignore methods such as $process->run(); block only direct global
            // calls whose behavior bypasses Laravel's normal safety boundaries.
            $previous = previousSignificantToken($tokens, $index);
            if ($previous === T_OBJECT_OPERATOR || $previous === T_NULLSAFE_OBJECT_OPERATOR || $previous === T_DOUBLE_COLON) {
                continue;
            }
            $next = nextSignificantToken($tokens, $index);
            if ($next === '(') {
                $findings[] = "$relative:$line: direct $name() call is forbidden in production PHP";
            }
        }

        if (preg_match('/CURLOPT_SSL_VERIFYPEER\s*,\s*false/i', $source, $match, PREG_OFFSET_CAPTURE)) {
            $findings[] = "$relative:" . lineAtOffset($source, $match[0][1]) . ': TLS peer verification may not be disabled';
        }
        if (preg_match('/CURLOPT_SSL_VERIFYHOST\s*,\s*0\b/i', $source, $match, PREG_OFFSET_CAPTURE)) {
            $findings[] = "$relative:" . lineAtOffset($source, $match[0][1]) . ': TLS host verification may not be disabled';
        }
        if (preg_match('/DB::(?:unprepared|raw)\s*\(\s*"[^"\n]*\$\{/i', $source, $match, PREG_OFFSET_CAPTURE)) {
            $findings[] = "$relative:" . lineAtOffset($source, $match[0][1]) . ': interpolated raw SQL is forbidden';
        }
    }
}

$envExample = $root . '/.env.example';
if (!is_file($envExample)) {
    $findings[] = '.env.example: required production environment template is missing';
} else {
    $env = (string) file_get_contents($envExample);
    foreach ([
        '/^APP_DEBUG\s*=\s*true\s*$/mi' => 'APP_DEBUG must remain false in the production template',
        '/^SESSION_SECURE_COOKIE\s*=\s*false\s*$/mi' => 'secure session cookies may not be disabled',
        '/^SESSION_HTTP_ONLY\s*=\s*false\s*$/mi' => 'HttpOnly session cookies may not be disabled',
    ] as $pattern => $message) {
        if (preg_match($pattern, $env, $match, PREG_OFFSET_CAPTURE)) {
            $findings[] = '.env.example:' . lineAtOffset($env, $match[0][1]) . ': ' . $message;
        }
    }
}

if ($findings !== []) {
    fwrite(STDERR, "SAFA PHP security static scan failed:\n");
    foreach ($findings as $finding) {
        fwrite(STDERR, "- $finding\n");
    }
    exit(2);
}

echo "SAFA PHP security static scan passed.\n";

function previousSignificantToken(array $tokens, int $index): int|string|null
{
    for ($i = $index - 1; $i >= 0; $i--) {
        $token = $tokens[$i];
        if (is_array($token)) {
            if (in_array($token[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
                continue;
            }
            return $token[0];
        }
        if (trim($token) !== '') {
            return $token;
        }
    }
    return null;
}

function nextSignificantToken(array $tokens, int $index): int|string|null
{
    $count = count($tokens);
    for ($i = $index + 1; $i < $count; $i++) {
        $token = $tokens[$i];
        if (is_array($token)) {
            if (in_array($token[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
                continue;
            }
            return $token[0];
        }
        if (trim($token) !== '') {
            return $token;
        }
    }
    return null;
}

function lineAtOffset(string $source, int $offset): int
{
    return substr_count(substr($source, 0, $offset), "\n") + 1;
}
