<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow">
    <title>SAFA • System Update</title>
    <link rel="icon" type="image/png" href="/safa-logo.png">
    <style>
        :root{--green:#064e3b;--green-dark:#022c22;--gold:#d4a72c;--page:#f4f7f5;--text:#10231d;--muted:#667a72;--border:#dce7e2;--danger:#b42318}
        *{box-sizing:border-box}body{margin:0;min-height:100vh;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:var(--page);color:var(--text);display:flex;align-items:center;justify-content:center;padding:16px}
        .shell{width:min(640px,100%)}.card{background:#fff;border:1px solid var(--border);border-radius:18px;overflow:hidden;box-shadow:0 16px 48px rgba(2,44,34,.08)}
        .hero{background:var(--green-dark);color:#fff;text-align:center;padding:28px 22px 24px}.logo{display:block;width:66px;height:66px;object-fit:contain;margin:0 auto 11px;background:#fff;border-radius:14px;padding:7px}.brand{font-size:11px;letter-spacing:.16em;font-weight:800;color:#e4bf52;text-transform:uppercase}.hero h1{margin:7px 0 5px;font-size:25px}.hero p{margin:0;color:#d8e9e3;font-size:13px;line-height:1.55}
        .body{padding:22px}.notice{display:flex;gap:11px;background:#fff8df;border:1px solid #f0d98c;border-radius:12px;padding:13px;margin-bottom:18px}.notice strong{display:block;margin-bottom:3px;color:#6e5310;font-size:13px}.notice span{color:#735f2a;font-size:12px;line-height:1.5}.section-label{font-size:12px;font-weight:800;color:var(--muted);margin-bottom:8px}.migration-list{border:1px solid var(--border);border-radius:11px;background:#fbfdfc;overflow:auto;max-height:220px;margin-bottom:18px}.migration{display:flex;align-items:center;gap:9px;padding:10px 12px;border-bottom:1px solid #edf2ef;font:11px ui-monospace,SFMono-Regular,Menlo,monospace;color:#385048}.migration:last-child{border-bottom:0}.dot{width:7px;height:7px;border-radius:50%;background:var(--gold);flex:0 0 auto}.error{background:#fff1f0;border:1px solid #f5c6c2;color:var(--danger);border-radius:11px;padding:11px 12px;margin-bottom:15px;font-size:12px;line-height:1.5}.btn{width:100%;border:0;border-radius:11px;padding:13px 16px;background:var(--green);color:#fff;font:800 13px inherit;cursor:pointer}.btn:disabled{opacity:.65;cursor:wait}.meta{text-align:center;color:var(--muted);font-size:11px;padding:0 22px 20px;line-height:1.5}
        @media(max-width:520px){body{padding:10px}.body{padding:16px}.hero{padding:24px 16px 20px}.hero h1{font-size:22px}}
    </style>
</head>
<body>
<div class="shell">
    <div class="card">
        <header class="hero">
            <img class="logo" src="/safa-logo.png" alt="SAFA">
            <div class="brand">SAFA</div>
            <h1>System Update Required</h1>
            <p>New application files are deployed. The database must be brought to the matching schema before normal use continues.</p>
        </header>
        <main class="body">
            @if(session('error'))
                <div class="error">{{ session('error') }}</div>
            @endif

            <div class="notice">
                <div aria-hidden="true">🛡️</div>
                <div>
                    <strong>Controlled database update</strong>
                    <span>Only versioned pending migrations from this SAFA release will run. The updater never runs migrate:fresh, migrate:reset, or migrate:refresh.</span>
                </div>
            </div>

            <div class="section-label">Pending database updates ({{ count($pendingMigrations) }})</div>
            <div class="migration-list">
                @foreach($pendingMigrations as $migration)
                    <div class="migration"><span class="dot"></span><span>{{ $migration }}</span></div>
                @endforeach
            </div>

            <form action="{{ route('system.update.process') }}" method="POST" onsubmit="this.querySelector('button').disabled=true;this.querySelector('button').textContent='Updating SAFA…';">
                @csrf
                <button class="btn" type="submit">Run Update</button>
            </form>
        </main>
        <div class="meta">Only an authenticated, activated SuperAdmin can run this update. Normal browser access resumes automatically after all migrations complete.</div>
    </div>
</div>
</body>
</html>
