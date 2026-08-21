<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>{{ $language === 'bn' ? 'সিস্টেম আপডেট — SAFA' : 'System Update — SAFA' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body class="auth-body">
<main class="auth-layout">
    <div class="auth-shell">
        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $language === 'bn' ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ $language === 'en' ? 'active' : '' }}" href="{{ route('release.update.show', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $language === 'bn' ? 'active' : '' }}" href="{{ route('release.update.show', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <section class="auth-card" aria-labelledby="release-update-title">
            <header class="auth-brand">
                <div class="auth-logo-wrap"><img class="auth-logo" src="{{ url('/safa-logo.png') }}" alt="SAFA"></div>
                <h1>SAFA</h1>
            </header>

            <h2 id="release-update-title">{{ $language === 'bn' ? 'সিস্টেম আপডেট প্রস্তুত' : 'System Update Ready' }}</h2>
            <p>{{ $language === 'bn' ? 'নতুন আপডেটটি সম্পন্ন করতে নিচের বাটনে ক্লিক করুন।' : 'Click the button below to finish installing the new update.' }}</p>

            @if (session('info'))
                <div class="alert" role="status">{{ session('info') }}</div>
            @endif
            @if (session('error'))
                <div class="alert alert-error" role="alert">{{ session('error') }}</div>
            @endif

            <form method="post" action="{{ route('release.update.run') }}" class="stack-form">
                @csrf
                <input type="hidden" name="language" value="{{ $language }}">
                <button class="primary-button wide" type="submit" data-testid="run-release-update">
                    {{ $language === 'bn' ? 'আপডেট চালান' : 'Run Update' }}
                </button>
            </form>
        </section>
    </div>
</main>
</body>
</html>
