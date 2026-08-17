@php
    $bn = session('safa_web_language', 'en') === 'bn';
@endphp
<!doctype html>
<html lang="{{ $bn ? 'bn' : 'en' }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • {{ $recoveryMode ? 'System Maintenance' : ($bn ? 'ডাটাবেজ আপডেট' : 'Database Update') }}</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="system-maintenance-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>{{ $recoveryMode ? 'System maintenance' : ($bn ? 'নিরাপদ ডাটাবেজ রক্ষণাবেক্ষণ' : 'Safe database maintenance') }}</span></div>
        </header>

        <h1 id="system-maintenance-title">{{ $recoveryMode ? 'System Maintenance' : ($bn ? 'ডাটাবেজ আপডেট' : 'Database Update') }}</h1>

        @if(session('success'))
            <div class="alert alert-success" role="status">{{ session('success') }}</div>
        @endif
        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        @if(!$recoveryMode)
            <div class="settings-grid">
                <section class="surface-card span-2" id="database-update-center">
                    <div>
                        <h2>{{ $bn ? 'ডাটাবেজ আপডেট' : 'Database Update' }}</h2>
                        <p>{{ $bn ? 'নতুন স্কিমা ও অনুমোদিত রেফারেন্স ডাটা নিরাপদভাবে প্রয়োগ করুন।' : 'Apply pending schema changes and approved reference data updates safely.' }}</p>
                    </div>

                    @if($pendingMigrations)
                        <div class="warning-box" data-database-update-state="pending">
                            <strong>{{ count($pendingMigrations) }} {{ $bn ? 'টি মাইগ্রেশন বাকি আছে' : 'pending migration' . (count($pendingMigrations) === 1 ? '' : 's') }}</strong>
                            <span>{{ $bn ? 'একবার Run Database Update চাপলেই সব forward migration ও release data update চলবে।' : 'Run Database Update once to apply all forward migrations and release data updates.' }}</span>
                        </div>
                        <ul class="system-list">
                            @foreach($pendingMigrations as $migration)
                                <li>{{ $migration }}</li>
                            @endforeach
                        </ul>
                    @else
                        <div class="success-box" data-database-update-state="current">{{ $bn ? 'বর্তমান স্কিমা আপ টু ডেট। প্রয়োজনীয় release data update আবার চালানো নিরাপদ।' : 'Schema is current. Re-running the approved release data updater is safe and idempotent.' }}</div>
                    @endif

                    <form action="{{ route('system.update.run') }}" method="POST" class="stack-form">
                        @csrf
                        <button class="primary-button wide" data-maintenance-action="database-update" type="submit">{{ $bn ? 'ডাটাবেজ আপডেট রান করুন' : 'Run Database Update' }}</button>
                    </form>

                    <p class="system-note">{{ $bn ? 'এই অপশন migrate:fresh, rollback, reset বা business data truncate করে না।' : 'This action uses forward migrations only. It does not run migrate:fresh, rollback, reset, or truncate business data.' }}</p>
                    <a class="secondary-button wide" href="{{ route('safa.app') }}">{{ $bn ? 'অ্যাপে ফিরুন' : 'Back to application' }}</a>
                </section>
            </div>
        @endif

        @if($recoveryMode)
            <div class="warning-box">
                <strong>Recovery mode</strong>
                <span>No activated Super Admin is available. A server-configured maintenance key is required.</span>
            </div>

            @php
                $seedBlockedByMigrations = (bool) $pendingMigrations;
                $seedBlockedByAdminConfig = !$initialAdminConfigured;
                $seedDisabled = $seedBlockedByMigrations || $seedBlockedByAdminConfig;
            @endphp

            <div class="settings-grid">
                <section class="surface-card">
                    <div>
                        <h2>Database Migration</h2>
                        <p>{{ count($pendingMigrations) }} pending migration{{ count($pendingMigrations) === 1 ? '' : 's' }}</p>
                    </div>

                    <form action="{{ route('system.update.migrate') }}" method="POST" class="stack-form">
                        @csrf
                        <label>
                            <span>Maintenance key</span>
                            <input type="password" name="maintenance_token" autocomplete="off" required>
                        </label>
                        <button class="primary-button wide" data-maintenance-action="migrate" type="submit" @disabled(!$pendingMigrations)>Run Migration</button>
                    </form>
                </section>

                <section class="surface-card">
                    <div>
                        <h2>Reference &amp; Admin Data</h2>
                        <p>Safe, idempotent production seed.</p>
                    </div>

                    @if($seedBlockedByMigrations)
                        <div class="info-box" data-seed-state="migration-required">Run Migration first.</div>
                    @elseif($seedBlockedByAdminConfig)
                        <div class="warning-box" data-seed-state="admin-config-required">Initial Super Admin server configuration is required before seeding.</div>
                    @else
                        <div class="success-box" data-seed-state="ready">Seed is ready.</div>
                    @endif

                    <form action="{{ route('system.update.seed') }}" method="POST" class="stack-form">
                        @csrf
                        <label>
                            <span>Maintenance key</span>
                            <input type="password" name="maintenance_token" autocomplete="off" required>
                        </label>
                        <button class="secondary-button wide" data-maintenance-action="seed" type="submit" @disabled($seedDisabled)>Run Seed</button>
                    </form>
                </section>

                @if($initialSuperAdminBootstrapAvailable)
                    <section class="surface-card" data-bootstrap-state="available">
                        <div>
                            <h2>Initial Super Admin</h2>
                            <p>No Admin or Super Admin exists. Create the first Super Admin without exposing database credentials.</p>
                        </div>
                        <a class="primary-button wide" href="{{ route('install.superadmin.show') }}">Create Super Admin</a>
                    </section>
                @endif
            </div>
        @endif
    </section>
</main>
</body>
</html>
