<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="color-scheme" content="light dark">
    <title>{{ $language === 'bn' ? 'ডাটা মাইগ্রেশন — SAFA' : 'Data Migration — SAFA' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body class="auth-body">
<main class="auth-layout">
    <div class="auth-shell">
        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $language === 'bn' ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ $language === 'en' ? 'active' : '' }}" href="{{ route('frontend.migration.show', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $language === 'bn' ? 'active' : '' }}" href="{{ route('frontend.migration.show', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <section class="auth-card" aria-labelledby="migration-title">
            <header class="auth-brand">
                <div class="auth-logo-wrap"><img class="auth-logo" src="{{ url('/safa-logo.png') }}" alt="SAFA"></div>
                <h1>SAFA</h1>
            </header>

            <h2 id="migration-title">{{ $language === 'bn' ? 'প্রথমবার ডাটা মাইগ্রেশন' : 'First-time Data Migration' }}</h2>

            <p>
                {{ $language === 'bn'
                    ? 'এই অপশনটি শুধু একবার দেখা যাবে। ডাটাবেজে পুরনো ডাটা থাকুক বা না থাকুক, নিচের বাটনে ক্লিক করলে নিরাপদ forward migration চালু হবে। বিদ্যমান business data reset, truncate বা migrate:fresh করা হবে না।'
                    : 'This option is shown only once. Whether the database already contains data or not, the button below runs only safe forward migrations. Existing business data is not reset, truncated, or migrated fresh.' }}
            </p>

            <p>
                {{ $language === 'bn'
                    ? 'সফল হলে এই page এবং migration action স্থায়ীভাবে বন্ধ হয়ে যাবে। ভবিষ্যতের migration শুধু SuperAdmin /update থেকে চলবে।'
                    : 'After a successful run, this page and migration action are permanently closed. Future migrations are available only from the SuperAdmin /update flow.' }}
            </p>

            @if (session('success'))
                <div class="alert alert-success" role="status">{{ session('success') }}</div>
            @endif
            @if (session('info'))
                <div class="alert" role="status">{{ session('info') }}</div>
            @endif
            @if (session('error'))
                <div class="alert alert-error" role="alert">{{ session('error') }}</div>
            @endif

            <div class="alert" role="status">
                {{ $language === 'bn' ? 'Pending migration:' : 'Pending migrations:' }} {{ count($pendingMigrations) }}
            </div>

            @if ($requiresSetupCode)
                <div class="alert" role="note" data-testid="empty-database-ownership-proof">
                    {{ $language === 'bn'
                        ? 'ডাটাবেজ সম্পূর্ণ খালি, তাই প্রথম SuperAdmin takeover ঠেকাতে server-private one-time setup code দিতে হবে। cPanel/server থেকে এই file খুলুন:'
                        : 'The database is completely empty, so the server-private one-time setup code is required to prevent first-SuperAdmin takeover. Read it from this cPanel/server file:' }}
                    <strong>{{ $setupCodePath }}</strong>
                </div>
            @endif

            <form method="post" action="{{ route('frontend.migration.run') }}" class="stack-form">
                @csrf
                <input type="hidden" name="language" value="{{ $language }}">
                @if ($requiresSetupCode)
                    <label>
                        <span>{{ $language === 'bn' ? 'One-time setup code' : 'One-time setup code' }}</span>
                        <input type="password" name="setup_code" maxlength="32" minlength="32" autocomplete="off" required data-testid="frontend-migration-setup-code">
                    </label>
                @endif
                <button class="primary-button wide" type="submit" data-testid="run-data-migration">
                    {{ $language === 'bn' ? 'ডাটা মাইগ্রেশন চালান' : 'Run Data Migration' }}
                </button>
            </form>
        </section>
    </div>
</main>
</body>
</html>
