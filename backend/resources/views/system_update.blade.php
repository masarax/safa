<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • System Maintenance</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="system-maintenance-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>System maintenance</span></div>
        </header>

        <h1 id="system-maintenance-title">System Maintenance</h1>

        @if(session('success'))
            <div class="alert alert-success" role="status">{{ session('success') }}</div>
        @endif
        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        @if($recoveryMode)
            <div class="warning-box">
                <strong>Recovery mode</strong>
                <span>No activated Super Admin is available. A server-configured maintenance key is required.</span>
            </div>
        @endif

        <div class="settings-grid">
            <section class="surface-card">
                <div>
                    <h2>Database Migration</h2>
                    <p>{{ count($pendingMigrations) }} pending migration{{ count($pendingMigrations) === 1 ? '' : 's' }}</p>
                </div>

                @if($pendingMigrations && !$recoveryMode)
                    <ul class="system-list">
                        @foreach($pendingMigrations as $migration)
                            <li>{{ $migration }}</li>
                        @endforeach
                    </ul>
                @endif

                <form action="{{ route('system.update.migrate') }}" method="POST" class="stack-form">
                    @csrf
                    @if($recoveryMode)
                        <label>
                            <span>Maintenance key</span>
                            <input type="password" name="maintenance_token" autocomplete="off" required>
                        </label>
                    @endif
                    <button class="primary-button wide" type="submit" @disabled(!$pendingMigrations)>Run Migration</button>
                </form>
            </section>

            <section class="surface-card">
                <div>
                    <h2>Reference &amp; Admin Data</h2>
                    <p>Safe, idempotent production seed.</p>
                </div>

                @if($recoveryMode && !$initialAdminConfigured)
                    <div class="alert alert-error">Initial Super Admin server configuration is required before seeding.</div>
                @endif

                <form action="{{ route('system.update.seed') }}" method="POST" class="stack-form">
                    @csrf
                    @if($recoveryMode)
                        <label>
                            <span>Maintenance key</span>
                            <input type="password" name="maintenance_token" autocomplete="off" required>
                        </label>
                    @endif
                    <button class="secondary-button wide" type="submit" @disabled((bool) $pendingMigrations)>Run Seed</button>
                </form>
            </section>
        </div>
    </section>
</main>
</body>
</html>
