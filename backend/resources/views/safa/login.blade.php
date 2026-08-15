<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light dark">
    <title>SAFA — {{ $language === 'bn' ? 'লগইন' : 'Sign in' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body class="auth-body">
<main class="auth-layout">
    <section class="auth-brand" aria-labelledby="brand-title">
        <img class="auth-logo" src="{{ url('/safa-logo.png') }}" alt="SAFA">
        <p class="eyebrow">SAFA Financial Operations</p>
        <h1 id="brand-title">{{ $language === 'bn' ? 'নিরাপদ ব্যবসা ব্যবস্থাপনা, মোবাইল ও ওয়েবে' : 'Secure business management on mobile and web' }}</h1>
        <p>{{ $language === 'bn' ? 'একই অনুমতি, অ্যাকাউন্ট আইসোলেশন এবং সার্ভার-নিয়ন্ত্রিত ডেটা নীতির মাধ্যমে আপনার অনুমোদিত কাজ পরিচালনা করুন।' : 'Use the same server-enforced permissions, account isolation, and business data rules as the Android application.' }}</p>
    </section>

    <section class="auth-card" aria-labelledby="login-title">
        <div class="language-switch" aria-label="Language">
            <a class="{{ $language === 'en' ? 'active' : '' }}" href="{{ route('safa.login', ['lang' => 'en']) }}">English</a>
            <a class="{{ $language === 'bn' ? 'active' : '' }}" href="{{ route('safa.login', ['lang' => 'bn']) }}">বাংলা</a>
        </div>

        <p class="eyebrow">{{ $language === 'bn' ? 'SAFA অ্যাকাউন্ট' : 'SAFA account' }}</p>
        <h2 id="login-title">{{ $language === 'bn' ? 'লগইন করুন' : 'Sign in' }}</h2>
        <p class="muted">{{ $language === 'bn' ? 'মোবাইল নম্বর অথবা ইমেইল এবং আপনার বিদ্যমান পিন/পাসওয়ার্ড ব্যবহার করুন।' : 'Use your mobile number or email with your existing PIN/password.' }}</p>

        @if ($errors->any())
            <div class="alert alert-error" role="alert">
                {{ $language === 'bn' ? 'লগইন তথ্য সঠিক নয়। আবার চেষ্টা করুন।' : 'The sign-in details are not valid. Please try again.' }}
            </div>
        @endif

        <form method="post" action="{{ route('safa.login.submit') }}" class="stack-form" autocomplete="on">
            @csrf
            <input type="hidden" name="language" value="{{ $language }}">
            <label>
                <span>{{ $language === 'bn' ? 'মোবাইল নম্বর অথবা ইমেইল' : 'Mobile number or email' }}</span>
                <input type="text" name="identity" value="{{ old('identity') }}" maxlength="255" autocomplete="username" inputmode="email" required autofocus>
            </label>
            <label>
                <span>{{ $language === 'bn' ? 'পিন / পাসওয়ার্ড' : 'PIN / password' }}</span>
                <input type="password" name="credential" minlength="6" maxlength="255" autocomplete="current-password" required>
            </label>
            <button class="button primary wide" type="submit">{{ $language === 'bn' ? 'নিরাপদ লগইন' : 'Secure sign in' }}</button>
        </form>

        <p class="security-note">{{ $language === 'bn' ? 'ব্রাউজার লগইন HttpOnly সেশন, CSRF সুরক্ষা এবং নিরাপদ কুকি ব্যবহার করে। Android API কী ব্রাউজারে প্রকাশ করা হয় না।' : 'Browser access uses HttpOnly sessions, CSRF protection, and secure cookies. The Android API client key is never exposed to browser JavaScript.' }}</p>
    </section>
</main>
</body>
</html>
