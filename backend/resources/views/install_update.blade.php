<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SAFA • Database Update</title>
    <link rel="icon" type="image/png" href="{{ route('branding.logo') }}">
    <link rel="apple-touch-icon" href="{{ route('branding.logo') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root{--green:#064e3b;--green-dark:#022c22;--gold:#d4a72c;--page:#f4f7f5;--text:#10231d;--muted:#667a72;--border:#dce7e2;--danger:#b42318}
        *{box-sizing:border-box}body{margin:0;min-height:100vh;font-family:Inter,'Hind Siliguri',sans-serif;background:var(--page);color:var(--text);display:flex;align-items:center;justify-content:center;padding:16px}
        .shell{width:min(620px,100%)}.card{background:#fff;border:1px solid var(--border);border-radius:18px;overflow:hidden}
        .hero{background:var(--green-dark);color:#fff;text-align:center;padding:26px 22px 22px;position:relative}.logo{display:block;width:68px;height:68px;object-fit:contain;margin:0 auto 10px;background:#fff;border-radius:14px;padding:7px}.brand{font-size:11px;letter-spacing:.14em;font-weight:700;color:#e4bf52;text-transform:uppercase}.hero h1{margin:7px 0 5px;font-size:24px;line-height:1.25}.hero p{margin:0;color:#d8e9e3;font-size:13px;line-height:1.5}.lang{position:absolute;right:14px;top:14px;display:flex;padding:2px;border-radius:16px;background:rgba(255,255,255,.1)}.lang button{border:0;background:transparent;color:#d8e9e3;padding:4px 8px;border-radius:12px;font-size:10px;font-weight:700;cursor:pointer}.lang button.active{background:#fff;color:var(--green)}
        .body{padding:22px}.notice{display:flex;gap:11px;background:#fff8df;border:1px solid #f0d98c;border-radius:12px;padding:12px;margin-bottom:18px}.notice-icon{width:30px;height:30px;display:grid;place-items:center;border-radius:9px;background:#fff0bd;flex:0 0 auto}.notice strong{display:block;margin-bottom:2px;color:#6e5310;font-size:13px}.notice span{color:#735f2a;font-size:12px;line-height:1.45}.section-label{font-size:12px;font-weight:700;color:var(--muted);margin-bottom:7px}.migration-list{border:1px solid var(--border);border-radius:11px;background:#fbfdfc;overflow:auto;max-height:190px;margin-bottom:18px}.migration{display:flex;align-items:center;gap:9px;padding:9px 12px;border-bottom:1px solid #edf2ef;font:11px ui-monospace,SFMono-Regular,Menlo,monospace;color:#385048}.migration:last-child{border-bottom:0}.dot{width:7px;height:7px;border-radius:50%;background:var(--gold);flex:0 0 auto}.error{background:#fff1f0;border:1px solid #f5c6c2;color:var(--danger);border-radius:11px;padding:11px 12px;margin-bottom:15px;font-size:12px;line-height:1.45}.btn{width:100%;border:0;border-radius:11px;padding:13px 16px;background:var(--green);color:#fff;font:700 13px Inter,'Hind Siliguri',sans-serif;cursor:pointer}.btn:disabled{opacity:.7;cursor:wait}.spinner{display:none;width:14px;height:14px;border:2px solid rgba(255,255,255,.35);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}.btn.loading .spinner{display:inline-block}.footer{text-align:center;color:var(--muted);font-size:10px;padding:0 22px 18px}@keyframes spin{to{transform:rotate(360deg)}}
        @media(max-width:520px){body{padding:10px}.hero{padding:24px 16px 20px}.body{padding:16px}.logo{width:62px;height:62px}.hero h1{font-size:21px}}
    </style>
</head>
<body>
<div class="shell"><div class="card">
    <header class="hero">
        <div class="lang"><button id="en" class="active" type="button" onclick="setLang('en')">EN</button><button id="bn" type="button" onclick="setLang('bn')">বাংলা</button></div>
        <img class="logo" src="{{ route('branding.logo') }}" alt="SAFA">
        <div class="brand">SAFA</div>
        <h1 id="title">Database Migration</h1>
        <p id="subtitle">A database update is required.</p>
    </header>
    <main class="body">
        @if(session('error'))<div class="error">{{ session('error') }}</div>@endif
        <div class="notice"><div class="notice-icon">🛡️</div><div><strong id="notice-title">Safe update</strong><span id="notice-text">Only pending migrations will run. Existing data is preserved.</span></div></div>
        <div class="section-label" id="pending-label">Pending migrations ({{ count($pendingMigrations) }})</div>
        <div class="migration-list">@foreach($pendingMigrations as $migration)<div class="migration"><span class="dot"></span><span>{{ $migration }}</span></div>@endforeach</div>
        <form action="{{ $updateUrl }}" method="POST" onsubmit="startUpdate(event)"><button class="btn" id="run" type="submit"><span class="spinner"></span><span id="run-text">Run Migration</span></button></form>
    </main>
    <div class="footer">SAFA • Secure database update</div>
</div></div>
<script>
const copy={en:{title:'Database Migration',subtitle:'A database update is required.',notice:'Safe update',noticeText:'Only pending migrations will run. Existing data is preserved.',pending:'Pending migrations ({{ count($pendingMigrations) }})',run:'Run Migration',wait:'Updating…'},bn:{title:'ডাটাবেস মাইগ্রেশন',subtitle:'ডাটাবেস আপডেট প্রয়োজন।',notice:'নিরাপদ আপডেট',noticeText:'শুধু pending migration চলবে। পুরোনো ডাটা থাকবে।',pending:'অপেক্ষমান মাইগ্রেশন ({{ count($pendingMigrations) }})',run:'মাইগ্রেশন চালান',wait:'আপডেট হচ্ছে…'}};
let lang='en';function setLang(l){lang=l;document.getElementById('en').classList.toggle('active',l==='en');document.getElementById('bn').classList.toggle('active',l==='bn');const t=copy[l];for(const [id,key] of [['title','title'],['subtitle','subtitle'],['notice-title','notice'],['notice-text','noticeText'],['pending-label','pending'],['run-text','run']])document.getElementById(id).textContent=t[key]}function startUpdate(e){const b=document.getElementById('run');b.disabled=true;b.classList.add('loading');document.getElementById('run-text').textContent=copy[lang].wait}
</script>
</body></html>