<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • {{ $language === 'bn' ? 'সুপার অ্যাডমিন সেটআপ' : 'Super Admin Setup' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="initial-superadmin-title">
        <header class="system-brand">
            <img src="{{ url('/safa-logo.png') }}" alt="SAFA">
            <div><strong>SAFA</strong><span>{{ $language === 'bn' ? 'প্রাথমিক নিরাপদ সেটআপ' : 'Secure initial setup' }}</span></div>
        </header>

        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $language === 'bn' ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ $language === 'en' ? 'active' : '' }}" href="{{ route('install.superadmin.show', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $language === 'bn' ? 'active' : '' }}" href="{{ route('install.superadmin.show', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <h1 id="initial-superadmin-title">{{ $language === 'bn' ? 'প্রথম সুপার অ্যাডমিন তৈরি করুন' : 'Create the first Super Admin' }}</h1>
        <p>{{ $language === 'bn' ? 'এই পেইজটি শুধুমাত্র তখনই পাওয়া যায় যখন কোনো অ্যাডমিন বা সুপার অ্যাডমিন নেই।' : 'This page is available only while no Admin or Super Admin exists.' }}</p>

        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif

        @unless($maintenanceConfigured)
            <div class="warning-box" role="status">
                <strong>{{ $language === 'bn' ? 'সার্ভার অনুমোদন প্রয়োজন' : 'Server authorization required' }}</strong>
                <span>{{ $language === 'bn' ? 'সেটআপ চালু করার আগে সার্ভারে maintenance authorization কনফিগার করতে হবে।' : 'Maintenance authorization must be configured on the server before setup can continue.' }}</span>
            </div>
        @endunless

        <form method="post" action="{{ route('install.superadmin.store') }}" class="stack-form" autocomplete="off">
            @csrf
            <input type="hidden" name="language" value="{{ $language }}">

            <label>
                <span>{{ $language === 'bn' ? 'নাম' : 'Name' }}</span>
                <input type="text" name="name" value="{{ old('name') }}" maxlength="255" autocomplete="name" required>
                @error('name')<span class="field-error" role="alert">{{ $message }}</span>@enderror
            </label>

            <label>
                <span>{{ $language === 'bn' ? 'মোবাইল নম্বর' : 'Mobile number' }}</span>
                <input type="text" name="mobile" value="{{ old('mobile') }}" maxlength="30" inputmode="tel" autocomplete="tel" required>
                @error('mobile')<span class="field-error" role="alert">{{ $message }}</span>@enderror
            </label>

            <label>
                <span>{{ $language === 'bn' ? 'ইমেইল' : 'Email' }}</span>
                <input type="email" name="email" value="{{ old('email') }}" maxlength="255" autocomplete="email" required>
                @error('email')<span class="field-error" role="alert">{{ $message }}</span>@enderror
            </label>

            <label>
                <span>{{ $language === 'bn' ? '৬-ডিজিট পিন' : '6-digit PIN' }}</span>
                <input type="password" name="pin" minlength="6" maxlength="6" inputmode="numeric" autocomplete="new-password" pattern="[0-9]{6}" required>
                @error('pin')<span class="field-error" role="alert">{{ $message }}</span>@enderror
            </label>

            <label>
                <span>{{ $language === 'bn' ? 'পিন নিশ্চিত করুন' : 'Confirm PIN' }}</span>
                <input type="password" name="pin_confirmation" minlength="6" maxlength="6" inputmode="numeric" autocomplete="new-password" pattern="[0-9]{6}" required>
            </label>

            <label>
                <span>{{ $language === 'bn' ? 'মেইনটেন্যান্স কী' : 'Maintenance key' }}</span>
                <input type="password" name="maintenance_token" maxlength="255" autocomplete="off" required @disabled(!$maintenanceConfigured)>
            </label>

            <button class="primary-button wide" type="submit" @disabled(!$maintenanceConfigured)>
                {{ $language === 'bn' ? 'সুপার অ্যাডমিন তৈরি করুন' : 'Create Super Admin' }}
            </button>
        </form>
    </section>
</main>
</body>
</html>
