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
    <title>SAFA • {{ $bn ? 'ডাটাবেজ আপডেট' : 'Database Update' }}</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="system-maintenance-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>{{ $bn ? 'নিরাপদ ডাটাবেজ রক্ষণাবেক্ষণ' : 'Safe database maintenance' }}</span></div>
        </header>

        <h1 id="system-maintenance-title">{{ $bn ? 'ডাটাবেজ আপডেট' : 'Database Update' }}</h1>

        @if(session('success'))
            <div class="alert alert-success" role="status">{{ session('success') }}</div>
        @endif
        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        <div class="settings-grid">
            <section class="surface-card span-2" id="database-update-center">
                @if($canRunUpdate)
                    <div>
                        <h2>{{ $bn ? 'ডাটাবেজ আপডেট' : 'Database Update' }}</h2>
                        <p>{{ $bn ? 'নতুন স্কিমা ও অনুমোদিত release data update নিরাপদভাবে প্রয়োগ করুন।' : 'Apply pending schema changes and approved release data updates safely.' }}</p>
                    </div>

                    @if($pendingMigrations)
                        <div class="warning-box" data-database-update-state="pending">
                            <strong>{{ count($pendingMigrations) }} {{ $bn ? 'টি মাইগ্রেশন বাকি আছে' : 'pending migration' . (count($pendingMigrations) === 1 ? '' : 's') }}</strong>
                            <span>{{ $bn ? 'Update Database চাপলে সব forward migration ও অনুমোদিত release data update চলবে।' : 'Update Database applies all forward migrations and the approved release data update.' }}</span>
                        </div>
                        <ul class="system-list">
                            @foreach($pendingMigrations as $migration)
                                <li>{{ $migration }}</li>
                            @endforeach
                        </ul>
                    @else
                        <div class="success-box" data-database-update-state="current">{{ $bn ? 'ডাটাবেজ স্কিমা আপ টু ডেট। অনুমোদিত release data updater পুনরায় চালানো নিরাপদ।' : 'Database schema is current. The approved release data updater is safe to run again.' }}</div>
                    @endif

                    <form action="{{ route('system.update.run') }}" method="POST" class="stack-form">
                        @csrf
                        <button class="primary-button wide" data-maintenance-action="database-update" type="submit">{{ $bn ? 'ডাটাবেজ আপডেট করুন' : 'Update Database' }}</button>
                    </form>

                    <p class="system-note">{{ $bn ? 'এই অপশন শুধু forward migration ও অনুমোদিত idempotent data update চালায়। কোনো fresh, rollback, reset, wipe বা truncate চালায় না।' : 'This action runs forward migrations and approved idempotent data updates only. It never runs fresh, rollback, reset, wipe, or truncate operations.' }}</p>
                    <a class="secondary-button wide" href="{{ route('safa.app') }}">{{ $bn ? 'অ্যাপে ফিরুন' : 'Back to application' }}</a>
                @else
                    <div data-database-update-state="restricted">
                        <h2>{{ $bn ? 'ডাটাবেজ আপডেট প্রয়োজন' : 'Database update required' }}</h2>
                        <p>{{ $bn ? 'এই রক্ষণাবেক্ষণ কাজটি শুধুমাত্র সক্রিয় SuperAdmin সম্পন্ন করতে পারেন। SuperAdmin দিয়ে লগইন করে আবার চেষ্টা করুন।' : 'Only an activated SuperAdmin can complete this maintenance operation. Sign in as SuperAdmin and try again.' }}</p>
                    </div>
                    <a class="secondary-button wide" href="{{ route('safa.logout') }}" onclick="event.preventDefault(); document.getElementById('maintenance-logout').submit();">{{ $bn ? 'অন্য অ্যাকাউন্টে লগইন' : 'Sign in with another account' }}</a>
                    <form id="maintenance-logout" method="POST" action="{{ route('safa.logout') }}" class="hidden">@csrf</form>
                @endif
            </section>
        </div>
    </section>
</main>
</body>
</html>
