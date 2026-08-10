<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SAFA System</title>
    <link rel="icon" type="image/png" href="{{ route('branding.logo') }}">
    <link rel="apple-touch-icon" href="{{ route('branding.logo') }}">
    <style>
        *{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:16px;background:#f4f7f5;color:#10231d;font-family:Inter,Arial,sans-serif}.card{width:min(440px,100%);background:#fff;border:1px solid #dce7e2;border-radius:16px;padding:24px;text-align:center}.logo{width:72px;height:72px;object-fit:contain;display:block;margin:0 auto 12px}.brand{font-size:11px;letter-spacing:.14em;font-weight:700;color:#d4a72c;text-transform:uppercase}.title{font-size:22px;font-weight:700;margin:7px 0}.status{display:inline-flex;align-items:center;gap:7px;padding:6px 10px;border-radius:16px;font-size:12px;font-weight:600;background:#ecfdf3;color:#067647;border:1px solid #abefc6;margin-top:8px}.dot{width:7px;height:7px;border-radius:50%;background:#12b76a}.update{margin-top:18px;padding:14px;border:1px solid #f0d98c;background:#fff8df;border-radius:12px;text-align:left}.update-title{font-size:13px;font-weight:700;color:#6e5310;margin-bottom:4px}.update-desc{font-size:12px;line-height:1.45;color:#735f2a;margin:0 0 11px}.btn{width:100%;border:0;border-radius:10px;padding:12px;background:#064e3b;color:#fff;font:700 13px Arial,sans-serif;cursor:pointer;text-align:center;text-decoration:none;display:block}.alert{padding:10px 12px;border-radius:10px;font-size:12px;font-weight:600;margin-bottom:12px}.success{background:#ecfdf3;color:#067647;border:1px solid #abefc6}.error{background:#fff1f0;color:#b42318;border:1px solid #f5c6c2}.footer{margin-top:16px;color:#667a72;font-size:10px}
    </style>
</head>
<body>
<div class="card">
    <img class="logo" src="{{ route('branding.logo') }}" alt="SAFA">
    <div class="brand">SAFA</div>
    <div class="title">SAFA System</div>

    @if(session('success'))<div class="alert success">{{ session('success') }}</div>@endif
    @if(session('error'))<div class="alert error">{{ session('error') }}</div>@endif

    @php
        $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';
        $pendingMigrations = \App\Http\Controllers\DatabaseUpdateController::pendingMigrations();
        $updateUrl = !empty($pendingMigrations)
            ? \Illuminate\Support\Facades\URL::temporarySignedRoute('install.update-process', now()->addMinutes(15))
            : null;
    @endphp

    @if (!$isInstalled)
        <script>window.location.href = "{{ url('/install') }}";</script>
    @elseif (!empty($pendingMigrations))
        <div class="update">
            <div class="update-title">Database update required</div>
            <p class="update-desc">{{ count($pendingMigrations) }} migration(s) are ready.</p>
            <a class="btn" href="{{ route('install.update-view') }}">Run Migration</a>
        </div>
    @else
        <div class="status"><span class="dot"></span>System Ready</div>
    @endif

    <div class="footer">SAFA • Secure system</div>
</div>
</body>
</html>