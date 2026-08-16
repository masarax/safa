<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="color-scheme" content="light dark">
    <title>{{ $appName }} — {{ $language === 'bn' ? 'লগইন' : 'Sign in' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body class="auth-body">
<main class="auth-layout">
    <div class="auth-shell">
        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $language === 'bn' ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ $language === 'en' ? 'active' : '' }}" href="{{ route('safa.login', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $language === 'bn' ? 'active' : '' }}" href="{{ route('safa.login', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <section class="auth-card" aria-labelledby="login-title">
            <header class="auth-brand">
                <div class="auth-logo-wrap"><img class="auth-logo" src="{{ $logoSource }}" alt="{{ $appName }}"></div>
                <h1>{{ $appName }}</h1>
            </header>

            <h2 id="login-title">{{ $language === 'bn' ? 'লগইন করুন' : 'Sign in' }}</h2>

            @if (session('success'))
                <div class="alert alert-success" role="status">{{ session('success') }}</div>
            @endif

            <form method="post" action="{{ route('safa.login.submit') }}" class="stack-form" autocomplete="on">
                @csrf
                <input type="hidden" name="language" value="{{ $language }}">
                <label>
                    <span>{{ $language === 'bn' ? 'মোবাইল নম্বর অথবা ইমেইল' : 'Mobile number or email' }}</span>
                    <div class="field-control"><span class="field-icon icon icon-phone" aria-hidden="true"></span><input type="text" name="identity" value="{{ old('identity') }}" maxlength="255" autocomplete="username" inputmode="email" placeholder="01700000000" required autofocus aria-describedby="identity-error"></div>
                    @error('identity')<span id="identity-error" class="field-error" role="alert">{{ $message }}</span>@enderror
                </label>
                <label>
                    <span>{{ $language === 'bn' ? 'পিন / পাসওয়ার্ড' : 'PIN / password' }}</span>
                    <div class="field-control"><span class="field-icon icon icon-lock" aria-hidden="true"></span><input type="password" name="credential" minlength="6" maxlength="255" autocomplete="current-password" required aria-describedby="credential-error"></div>
                    @error('credential')<span id="credential-error" class="field-error" role="alert">{{ $message }}</span>@enderror
                </label>

                @error('auth')
                    @php($failureType = $errors->first('auth') === 'email' ? 'email' : 'mobile')
                    <p class="auth-error-inline" role="alert" aria-live="assertive">
                        @if($language === 'bn')
                            {{ $failureType === 'email' ? 'ইমেইল অথবা পিন / পাসওয়ার্ড সঠিক নয়।' : 'মোবাইল নম্বর অথবা পিন / পাসওয়ার্ড সঠিক নয়।' }}
                        @else
                            {{ $failureType === 'email' ? 'Invalid email or PIN / password.' : 'Invalid mobile number or PIN / password.' }}
                        @endif
                    </p>
                @enderror

                <button class="primary-button wide" type="submit">{{ $language === 'bn' ? 'লগইন' : 'Sign in' }}</button>
            </form>
        </section>
    </div>
</main>
</body>
</html>
