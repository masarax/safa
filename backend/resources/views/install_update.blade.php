<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>SAFA • Database Update</title>
    <link rel="icon" type="image/png" href="{{ route('branding.logo') }}">
    <link rel="apple-touch-icon" href="{{ route('branding.logo') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --emerald-950:#022c22; --emerald-900:#064e3b; --emerald-800:#065f46;
            --gold-500:#d4a72c; --gold-400:#e4bf52; --gold-soft:#fff8df;
            --surface:#ffffff; --page:#f4f7f5; --text:#10231d; --muted:#667a72;
            --border:#dce7e2; --danger:#b42318; --danger-bg:#fff1f0;
        }
        *{box-sizing:border-box} body{margin:0;min-height:100vh;font-family:Inter,'Hind Siliguri',sans-serif;background:radial-gradient(circle at 50% 0,#e6f2ed 0,#f4f7f5 42%,#eef3f0 100%);color:var(--text);display:flex;align-items:center;justify-content:center;padding:24px}
        .shell{width:min(680px,100%);animation:rise .45s ease-out}.card{background:var(--surface);border:1px solid var(--border);border-radius:24px;overflow:hidden;box-shadow:0 22px 60px rgba(2,44,34,.12)}
        .hero{background:linear-gradient(135deg,var(--emerald-950),var(--emerald-800));color:#fff;text-align:center;padding:32px 28px 28px;position:relative}
        .hero:after{content:"";position:absolute;inset:auto -10% -60px;height:110px;background:rgba(212,167,44,.12);filter:blur(25px)}
        .logo{position:relative;z-index:1;width:82px;height:82px;object-fit:contain;background:#fff;border-radius:20px;padding:8px;box-shadow:0 10px 30px rgba(0,0,0,.2);margin-bottom:16px}
        .brand{font-size:12px;letter-spacing:.16em;font-weight:700;color:var(--gold-400);text-transform:uppercase}.hero h1{position:relative;z-index:1;margin:8px 0 8px;font-size:clamp(22px,4vw,30px);line-height:1.2}.hero p{position:relative;z-index:1;margin:0;color:#d8e9e3;font-size:14px;line-height:1.6}
        .body{padding:28px}.notice{display:flex;gap:14px;background:var(--gold-soft);border:1px solid #f0d98c;border-radius:16px;padding:16px;margin-bottom:22px}.notice-icon{width:36px;height:36px;display:grid;place-items:center;border-radius:11px;background:#fff0bd;flex:0 0 auto}.notice strong{display:block;margin-bottom:4px;color:#6e5310}.notice span{color:#735f2a;font-size:13px;line-height:1.55}
        .section-label{font-size:13px;font-weight:700;color:var(--muted);margin-bottom:9px}.migration-list{border:1px solid var(--border);border-radius:14px;background:#fbfdfc;overflow:auto;max-height:210px;margin-bottom:22px}.migration{display:flex;align-items:center;gap:10px;padding:11px 14px;border-bottom:1px solid #edf2ef;font:12px ui-monospace,SFMono-Regular,Menlo,monospace;color:#385048}.migration:last-child{border-bottom:0}.dot{width:8px;height:8px;border-radius:50%;background:var(--gold-500);box-shadow:0 0 0 4px rgba(212,167,44,.12);flex:0 0 auto}
        .error{background:var(--danger-bg);border:1px solid #f5c6c2;color:var(--danger);border-radius:14px;padding:13px 15px;margin-bottom:18px;font-size:13px;line-height:1.5}.actions{display:flex;gap:10px;flex-direction:column}.btn{width:100%;border:0;border-radius:13px;padding:14px 18px;font:700 14px Inter,'Hind Siliguri',sans-serif;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:9px;transition:transform .18s ease,box-shadow .18s ease,opacity .18s ease}.btn-primary{background:linear-gradient(135deg,var(--emerald-800),var(--emerald-900));color:#fff;box-shadow:0 10px 22px rgba(6,78,59,.22)}.btn-primary:hover{transform:translateY(-1px);box-shadow:0 14px 26px rgba(6,78,59,.28)}.btn:disabled{opacity:.7;cursor:wait;transform:none}.spinner{display:none;width:16px;height:16px;border:2px solid rgba(255,255,255,.35);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}.btn.loading .spinner{display:block}.footer{text-align:center;color:var(--muted);font-size:11px;padding:0 28px 24px}.secure{color:var(--emerald-800);font-weight:700}
        .lang{position:absolute;right:20px;top:20px;z-index:3;display:flex;padding:3px;border-radius:20px;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.16)}.lang button{border:0;background:transparent;color:#d8e9e3;padding:5px 9px;border-radius:15px;font-size:11px;font-weight:700;cursor:pointer}.lang button.active{background:#fff;color:var(--emerald-900)}
        @keyframes rise{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:none}} @keyframes spin{to{transform:rotate(360deg)}}
        @media(max-width:520px){body{padding:12px}.hero{padding:28px 20px 24px}.body{padding:20px}.logo{width:72px;height:72px}.lang{right:12px;top:12px}}
    </style>
</head>
<body>
<div class="shell">
    <div class="card">
        <header class="hero">
            <div class="lang"><button id="en" class="active" type="button" onclick="setLang('en')">EN</button><button id="bn" type="button" onclick="setLang('bn')">বাংলা</button></div>
            <img class="logo" src="{{ route('branding.logo') }}" alt="SAFA logo">
            <div class="brand">SAFA Account System</div>
            <h1 id="title">System Update &amp; Database Migration</h1>
            <p id="subtitle">A schema update is required. Your existing business data will not be intentionally deleted.</p>
        </header>
        <main class="body">
            @if(session('error'))
                <div class="error">⚠️ {{ session('error') }}</div>
            @endif
            <div class="notice">
                <div class="notice-icon">🛡️</div>
                <div><strong id="notice-title">Protected database update</strong><span id="notice-text">Only pending Laravel migrations will be executed. Existing records are preserved by the migration/update flow.</span></div>
            </div>
            <div class="section-label" id="pending-label">Pending migrations ({{ count($pendingMigrations) }})</div>
            <div class="migration-list">
                @foreach($pendingMigrations as $migration)
                    <div class="migration"><span class="dot"></span><span>{{ $migration }}</span></div>
                @endforeach
            </div>
            <form action="{{ $updateUrl }}" method="POST" onsubmit="startUpdate(event)">
                @csrf
                <div class="actions"><button class="btn btn-primary" id="run" type="submit"><span class="spinner"></span><span id="run-text">🚀 Run Database Migration Now</span></button></div>
            </form>
        </main>
        <div class="footer"><span class="secure">Secure update link</span> • expires automatically • SAFA</div>
    </div>
</div>
<script>
const copy={en:{title:'System Update & Database Migration',subtitle:'A schema update is required. Your existing business data will not be intentionally deleted.',notice:'Protected database update',noticeText:'Only pending Laravel migrations will be executed. Existing records are preserved by the migration/update flow.',pending:'Pending migrations ({{ count($pendingMigrations) }})',run:'🚀 Run Database Migration Now',wait:'Updating database schema… Please wait'},bn:{title:'সিস্টেম আপডেট ও ডাটাবেস মাইগ্রেশন',subtitle:'নতুন স্কিমা আপডেট প্রয়োজন। বিদ্যমান ব্যবসায়িক ডাটা ইচ্ছাকৃতভাবে মুছে ফেলা হবে না।',notice:'সুরক্ষিত ডাটাবেস আপডেট',noticeText:'শুধু pending Laravel migration চালানো হবে। আপডেট ফ্লো বিদ্যমান রেকর্ড সংরক্ষণ করার জন্য তৈরি করা হয়েছে।',pending:'অপেক্ষমান মাইগ্রেশন ({{ count($pendingMigrations) }})',run:'🚀 এখন ডাটাবেস মাইগ্রেশন চালান',wait:'ডাটাবেস আপডেট হচ্ছে… অপেক্ষা করুন'}};
let lang='en';function setLang(l){lang=l;document.getElementById('en').classList.toggle('active',l==='en');document.getElementById('bn').classList.toggle('active',l==='bn');const t=copy[l];document.getElementById('title').textContent=t.title;document.getElementById('subtitle').textContent=t.subtitle;document.getElementById('notice-title').textContent=t.notice;document.getElementById('notice-text').textContent=t.noticeText;document.getElementById('pending-label').textContent=t.pending;document.getElementById('run-text').textContent=t.run}function startUpdate(e){const b=document.getElementById('run');b.disabled=true;b.classList.add('loading');document.getElementById('run-text').textContent=copy[lang].wait}
</script>
</body>
</html>
