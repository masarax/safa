<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="light dark">
    <title>SAFA • First SuperAdmin</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body>
<main class="system-page">
    <section class="system-card" aria-labelledby="first-admin-title">
        <header class="system-brand">
            <img src="/safa-logo.png" alt="SAFA">
            <div><strong>SAFA</strong><span>Finish first-run setup</span></div>
        </header>

        <h1 id="first-admin-title">Create First SuperAdmin</h1>
        <p>The database migration is complete and the migration button is no longer available. Create the first administrator, then normal login takes over.</p>

        @if(session('success'))
            <div class="alert alert-success" role="status">{{ session('success') }}</div>
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
            <label>Name<input type="text" name="name" maxlength="255" required value="{{ old('name') }}" autocomplete="name"></label>
            <label>Mobile<input type="tel" name="mobile" maxlength="32" required value="{{ old('mobile') }}" autocomplete="tel"></label>
            <label>Email<input type="email" name="email" maxlength="255" required value="{{ old('email') }}" autocomplete="email"></label>
            <label>6-digit PIN<input type="password" name="pin" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required autocomplete="new-password"></label>
            <label>Confirm PIN<input type="password" name="pin_confirmation" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required autocomplete="new-password"></label>
            <button class="primary-button wide" data-first-run-action="create-superadmin" type="submit">Finish Setup</button>
        </form>

        <p class="system-note">No default password, PIN or bootstrap credential is stored in source code or environment configuration.</p>
    </section>
</main>
</body>
</html>
