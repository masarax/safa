@php($bn = request()->query('lang') === 'bn')
<!doctype html>
<html lang="{{ $bn ? 'bn' : 'en' }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>@yield('code') • @yield('title') • SAFA</title>
    <link rel="icon" type="image/svg+xml" href="{{ url('/favicon.svg') }}">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="system-error-title">
        <header class="system-brand">
            <img src="{{ url('/safa-logo.png') }}" alt="SAFA">
            <div>
                <strong>SAFA</strong>
                <span>{{ $bn ? 'সিস্টেম পেইজ' : 'System page' }}</span>
            </div>
        </header>

        <div class="warning-box" role="status">
            <strong>@yield('code')</strong>
            <span>@yield('eyebrow')</span>
        </div>

        <h1 id="system-error-title">@yield('title')</h1>
        <p>@yield('message')</p>

        <div class="stack-form">
            <a class="primary-button wide" href="{{ url('/' . ($bn ? '?lang=bn' : '')) }}">
                {{ $bn ? 'SAFA হোম / সেটআপে যান' : 'Go to SAFA home / setup' }}
            </a>
            <a class="secondary-button wide" href="{{ url('/login' . ($bn ? '?lang=bn' : '')) }}">
                {{ $bn ? 'লগইন পেইজ' : 'Sign-in page' }}
            </a>
        </div>

        <p class="system-note">{{ $bn
            ? 'সমস্যাটি চলতে থাকলে server log এবং সর্বশেষ deployed commit পরীক্ষা করুন।'
            : 'If this continues, check the server log and the latest deployed commit.' }}</p>
    </section>
</main>
</body>
</html>
