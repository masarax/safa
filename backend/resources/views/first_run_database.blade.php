<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • First-run database setup</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="first-run-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>One-time database initialization</span></div>
        </header>

        <h1 id="first-run-title">Initialize Database</h1>
        <p>This option is available only because the configured database is completely uninitialized. After a successful migration it disappears permanently.</p>

        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        <div class="warning-box" data-first-run-state="database">
            <strong>{{ count($pendingMigrations) }} pending migration{{ count($pendingMigrations) === 1 ? '' : 's' }}</strong>
            <span>Only reviewed forward migrations are allowed. Fresh, rollback, reset, wipe and truncate commands are never used.</span>
        </div>

        <form action="{{ route('setup.database.run') }}" method="POST" class="stack-form" autocomplete="off">
            @csrf
            <label>
                One-time setup code
                <input type="password" name="setup_code" minlength="32" maxlength="32" pattern="[A-Fa-f0-9]{32}" required autocomplete="off" spellcheck="false">
            </label>
            <p class="system-note">For takeover protection, read the code from the server-only file <code>{{ $setupCodePath }}</code>. The file is outside public storage and is deleted when migration succeeds.</p>
            <button class="primary-button wide" data-first-run-action="initialize-database" type="submit">Initialize Database</button>
        </form>

        <p class="system-note">The application continues to use the configured production database. This screen does not switch production to SQLite.</p>
    </section>
</main>
</body>
</html>
