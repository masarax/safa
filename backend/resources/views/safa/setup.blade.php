<!doctype html>
<html lang="en" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="color-scheme" content="light dark">
    <title>SAFA — Setup & maintenance</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
</head>
<body class="auth-body">
<main class="auth-layout">
    <div class="auth-shell">
        <section class="auth-card" aria-labelledby="setup-title">
            <header class="auth-brand">
                <div class="auth-logo-wrap"><img class="auth-logo" src="{{ url('/safa-logo.png') }}" alt="SAFA"></div>
                <h1>SAFA</h1>
                <p>Secure setup & maintenance</p>
            </header>

            <h2 id="setup-title">{{ $initialized ? 'System maintenance' : 'Initial production setup' }}</h2>
            <p class="auth-intro">
                @if($initialized)
                    Refresh safe reference data or continue to the authenticated database update screen. Existing business records are never reset here.
                @else
                    Create the first Super Admin and initialize the production database using additive migrations and idempotent seed data.
                @endif
            </p>

            @if (session('success'))
                <div class="alert alert-success" role="status">{{ session('success') }}</div>
            @endif
            @if (session('info'))
                <div class="alert alert-info" role="status">{{ session('info') }}</div>
            @endif
            @if (session('error'))
                <div class="alert alert-error" role="alert">{{ session('error') }}</div>
            @endif
            @if ($errors->any())
                <div class="alert alert-error" role="alert">{{ $errors->first() }}</div>
            @endif

            @if($initialized)
                <div class="security-note">
                    <strong>Database:</strong> {{ count($pendingMigrations) ? count($pendingMigrations) . ' migration(s) pending' : 'up to date' }}<br>
                    <strong>Android API credentials:</strong> {{ $apiCredentialsConfigured ? 'configured' : 'not configured in server environment' }}
                </div>

                <div class="stack-form">
                    @if(count($pendingMigrations))
                        <a class="primary-button wide" href="{{ route('system.update.show') }}">Run database update</a>
                    @else
                        <form method="post" action="{{ route('safa.setup.seed') }}">
                            @csrf
                            <button class="primary-button wide" type="submit">Refresh safe seed data</button>
                        </form>
                    @endif
                    <a class="secondary-button wide" href="{{ route('safa.app') }}">Open SAFA</a>
                </div>
            @else
                <form method="post" action="{{ route('safa.setup.bootstrap') }}" class="stack-form" autocomplete="off">
                    @csrf
                    <label>
                        <span>Setup key</span>
                        <div class="field-control"><span class="field-icon icon icon-lock" aria-hidden="true"></span><input type="password" name="setup_secret" maxlength="1024" autocomplete="off" required></div>
                    </label>
                    <p class="security-note">Use the server's <code>SAFA_SETUP_TOKEN</code>. For recovery on an already configured cPanel installation, the current database password is also accepted. The value is never stored by this form.</p>

                    <label>
                        <span>Super Admin name</span>
                        <div class="field-control"><span class="field-icon icon icon-user" aria-hidden="true"></span><input type="text" name="name" value="{{ old('name') }}" minlength="2" maxlength="100" autocomplete="name" required></div>
                    </label>
                    <label>
                        <span>Mobile number</span>
                        <div class="field-control"><span class="field-icon icon icon-phone" aria-hidden="true"></span><input type="tel" name="mobile" value="{{ old('mobile') }}" minlength="5" maxlength="30" inputmode="tel" autocomplete="tel" required></div>
                    </label>
                    <label>
                        <span>Email</span>
                        <div class="field-control"><span class="field-icon icon icon-user" aria-hidden="true"></span><input type="email" name="email" value="{{ old('email') }}" maxlength="255" autocomplete="email" required></div>
                    </label>
                    <label>
                        <span>6-digit PIN</span>
                        <div class="field-control"><span class="field-icon icon icon-lock" aria-hidden="true"></span><input type="password" name="pin" pattern="[0-9]{6}" inputmode="numeric" minlength="6" maxlength="6" autocomplete="new-password" required></div>
                    </label>
                    <label>
                        <span>Confirm PIN</span>
                        <div class="field-control"><span class="field-icon icon icon-lock" aria-hidden="true"></span><input type="password" name="pin_confirmation" pattern="[0-9]{6}" inputmode="numeric" minlength="6" maxlength="6" autocomplete="new-password" required></div>
                    </label>

                    <button class="primary-button wide" type="submit">Initialize database & create Super Admin</button>
                </form>

                <p class="security-note">This one-time flow never accepts arbitrary database hosts, never writes credentials into source control, and never runs destructive migrate/reset/fresh commands.</p>
            @endif
        </section>
    </div>
</main>
</body>
</html>
