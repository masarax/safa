@php($bn = ($language ?? 'en') === 'bn')
<!doctype html>
<html lang="{{ $bn ? 'bn' : 'en' }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • {{ $bn ? 'প্রথম SuperAdmin' : 'First SuperAdmin' }}</title>
    <link rel="icon" type="image/svg+xml" href="{{ url('/favicon.svg') }}">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="first-admin-title">
        <div class="auth-toolbar">
            <nav class="language-switch" aria-label="{{ $bn ? 'ভাষা নির্বাচন' : 'Language selection' }}">
                <a class="{{ !$bn ? 'active' : '' }}" href="{{ route('setup.admin.show', ['lang' => 'en']) }}" lang="en">English</a>
                <a class="{{ $bn ? 'active' : '' }}" href="{{ route('setup.admin.show', ['lang' => 'bn']) }}" lang="bn">বাংলা</a>
            </nav>
        </div>

        <header class="system-brand">
            <img src="{{ url('/safa-logo.png') }}" alt="SAFA">
            <div>
                <strong>SAFA</strong>
                <span>{{ $bn ? 'প্রথমবার সেটআপ সম্পন্ন করুন' : 'Finish first-run setup' }}</span>
            </div>
        </header>

        <h1 id="first-admin-title">{{ $bn ? 'প্রথম SuperAdmin তৈরি করুন' : 'Create First SuperAdmin' }}</h1>
        <p>{{ $bn
            ? 'ডাটাবেজ প্রস্তুত হয়েছে এবং migration action এখন স্থায়ীভাবে বন্ধ। এই browser session থেকেই প্রথম SuperAdmin তৈরি করে সেটআপ শেষ করুন।'
            : 'The database is prepared and the migration action is now permanently closed. Create the first SuperAdmin in this same browser session to finish setup.' }}</p>

        @if(session('success'))
            <div class="alert alert-success" role="status">{{ session('success') }}</div>
        @endif
        @if(session('info'))
            <div class="alert" role="status">{{ session('info') }}</div>
        @endif
        @if(session('error'))
            <div class="alert alert-error" role="alert">{{ session('error') }}</div>
        @endif
        @if($errors->any())
            <div class="alert alert-error" role="alert">
                <ul class="system-list">
                    @foreach($errors->all() as $error)<li>{{ $error }}</li>@endforeach
                </ul>
            </div>
        @endif

        <form action="{{ route('setup.admin.create') }}" method="POST" class="stack-form" autocomplete="off">
            @csrf
            <input type="hidden" name="language" value="{{ $bn ? 'bn' : 'en' }}">
            <label>{{ $bn ? 'নাম' : 'Name' }}<input type="text" name="name" maxlength="255" required value="{{ old('name') }}" autocomplete="name"></label>
            <label>{{ $bn ? 'মোবাইল' : 'Mobile' }}<input type="tel" name="mobile" maxlength="32" required value="{{ old('mobile') }}" autocomplete="tel"></label>
            <label>{{ $bn ? 'ইমেইল' : 'Email' }}<input type="email" name="email" maxlength="255" required value="{{ old('email') }}" autocomplete="email"></label>
            <label>{{ $bn ? '৬ সংখ্যার পিন' : '6-digit PIN' }}<input type="password" name="pin" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required autocomplete="new-password"></label>
            <label>{{ $bn ? 'পিন নিশ্চিত করুন' : 'Confirm PIN' }}<input type="password" name="pin_confirmation" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required autocomplete="new-password"></label>
            <button class="primary-button wide" data-first-run-action="create-superadmin" type="submit">{{ $bn ? 'সেটআপ সম্পন্ন করুন' : 'Finish Setup' }}</button>
        </form>

        <p class="system-note">{{ $bn
            ? 'Source code বা environment configuration-এ কোনো default password, PIN বা bootstrap credential রাখা হয় না।'
            : 'No default password, PIN or bootstrap credential is stored in source code or environment configuration.' }}</p>
    </section>
</main>
</body>
</html>
