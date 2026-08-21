@php($bn = ($language ?? 'en') === 'bn')
<!doctype html>
<html lang="{{ $bn ? 'bn' : 'en' }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • {{ $bn ? 'প্রথমবার ডাটাবেজ সেটআপ' : 'First-run database setup' }}</title>
    <link rel="icon" type="image/svg+xml" href="{{ url('/favicon.svg') }}">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="first-run-title">
        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $bn ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ !$bn ? 'active' : '' }}" href="{{ route('setup.database.show', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $bn ? 'active' : '' }}" href="{{ route('setup.database.show', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <header class="system-brand">
            <img src="{{ url('/safa-logo.png') }}" alt="SAFA">
            <div>
                <strong>SAFA</strong>
                <span>{{ $bn ? 'প্রথমবার সার্ভার ও ডাটাবেজ প্রস্তুতি' : 'First-time server and database preparation' }}</span>
            </div>
        </header>

        <h1 id="first-run-title">{{ $bn ? 'ডাটাবেজ প্রস্তুত করুন' : 'Prepare Database' }}</h1>
        <p>{{ $bn
            ? 'এই অপশনটি শুধু নতুন বা সম্পূর্ণ খালি, এখনো কারও দ্বারা ব্যবহার শুরু না করা ডাটাবেজে দেখা যাবে। সেটআপ শেষ হলে এটি স্থায়ীভাবে বন্ধ হয়ে যাবে।'
            : 'This option is available only for a new or empty, unclaimed database. It permanently closes after setup is completed.' }}</p>

        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        @if(count($pendingMigrations) > 0)
            <div class="warning-box" data-first-run-state="database">
                <strong>{{ count($pendingMigrations) }} {{ $bn ? 'টি নিরাপদ forward migration বাকি' : 'safe forward migration' . (count($pendingMigrations) === 1 ? '' : 's') . ' pending' }}</strong>
                <span>{{ $bn
                    ? 'Initialize Database চাপলে শুধু review করা forward migration এবং অনুমোদিত reference data update চলবে।'
                    : 'Initialize Database runs only reviewed forward migrations and approved reference-data updates.' }}</span>
            </div>
            <ul class="system-list">
                @foreach($pendingMigrations as $migration)
                    <li>{{ $migration }}</li>
                @endforeach
            </ul>
        @else
            <div class="success-box" data-first-run-state="prepared">
                {{ $bn
                    ? 'ডাটাবেজ স্কিমা আগে থেকেই প্রস্তুত, কিন্তু এখনো কোনো মালিক/ব্যবসার ডাটা নেই। নিচের নিরাপদ সেটআপ ধাপটি সম্পন্ন করুন।'
                    : 'The schema is already prepared, but the database is still empty and unclaimed. Complete the secure setup step below.' }}
            </div>
        @endif

        <form action="{{ route('setup.database.run') }}" method="POST" class="stack-form" autocomplete="off">
            @csrf
            <input type="hidden" name="language" value="{{ $bn ? 'bn' : 'en' }}">
            <label>
                <span>{{ $bn ? 'একবার ব্যবহারযোগ্য সেটআপ কোড' : 'One-time setup code' }}</span>
                <input type="password" name="setup_code" minlength="32" maxlength="32" pattern="[A-Fa-f0-9]{32}" required autocomplete="off" spellcheck="false">
            </label>
            <p class="system-note">{{ $bn
                ? 'নিরাপত্তার জন্য cPanel সার্ভারের এই private file থেকে কোডটি নিন:'
                : 'For takeover protection, read the code from this private file on the cPanel server:' }} <code>{{ $setupCodePath }}</code>. {{ $bn ? 'সফল সেটআপের পর file-টি মুছে যাবে।' : 'The file is deleted after successful setup.' }}</p>
            <button class="primary-button wide" data-first-run-action="initialize-database" type="submit">
                {{ $bn ? 'ডাটাবেজ ইনিশিয়ালাইজ করুন' : 'Initialize Database' }}
            </button>
        </form>

        <p class="system-note">{{ $bn
            ? 'Production সবসময় configured MySQL ব্যবহার করবে। এই screen production-কে SQLite-এ পরিবর্তন করে না এবং fresh/reset/wipe চালায় না।'
            : 'Production continues using the configured MySQL database. This screen never switches production to SQLite and never runs fresh/reset/wipe operations.' }}</p>
    </section>
</main>
</body>
</html>
