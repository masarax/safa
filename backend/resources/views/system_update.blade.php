<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • System Update</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="system-update-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>Secure business management</span></div>
        </header>

        <p class="eyebrow">System maintenance</p>
        <h1 id="system-update-title">System Update Required</h1>
        <p>New SAFA application files are ready. The database schema must be updated before normal use continues.</p>

        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        <div class="warning-box">
            <strong>Controlled database update.</strong> Only versioned pending migrations from this release will run. Destructive reset or refresh commands are never used.
        </div>

        <p class="section-label"><strong>Pending database updates ({{ count($pendingMigrations) }})</strong></p>
        <ul class="system-list">
            @foreach($pendingMigrations as $migration)
                <li>{{ $migration }}</li>
            @endforeach
        </ul>

        <form action="{{ route('system.update.process') }}" method="POST" onsubmit="this.querySelector('button').disabled=true;this.querySelector('button').textContent='Updating SAFA…';">
            @csrf
            <button class="primary-button wide" type="submit">Run Update</button>
        </form>
        <p class="security-note">Only an authenticated, activated SuperAdmin can run this update. Normal access resumes automatically after all migrations complete.</p>
    </section>
</main>
</body>
</html>
