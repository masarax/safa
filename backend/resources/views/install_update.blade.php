<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>SAFA System Update | Database Migration Required</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #f8fafc;
            --card-bg: #ffffff;
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --success: #16a34a;
            --danger: #dc2626;
            --text-dark: #0f172a;
            --text-muted: #64748b;
            --border-color: #e2e8f0;
            --input-bg: #f8fafc;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', 'Hind Siliguri', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-dark);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 1.5rem 0.75rem;
        }

        .installer-box {
            width: 100%;
            max-width: 620px;
            background: var(--card-bg);
            border-radius: 12px;
            border: 1px solid var(--border-color);
            overflow: hidden;
        }

        .header {
            padding: 1.75rem 1.5rem 1.25rem;
            text-align: center;
            background: #ffffff;
            border-bottom: 1px solid var(--border-color);
            position: relative;
        }

        .lang-switch {
            position: absolute;
            top: 1.25rem;
            right: 1.25rem;
            display: flex;
            background: #f1f5f9;
            padding: 2px;
            border-radius: 20px;
            border: 1px solid var(--border-color);
        }

        .lang-btn {
            background: transparent;
            border: none;
            padding: 0.25rem 0.65rem;
            border-radius: 16px;
            font-size: 0.75rem;
            font-weight: 700;
            color: var(--text-muted);
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .lang-btn.active {
            background: var(--primary);
            color: #ffffff;
        }

        .brand-badge {
            display: inline-block;
            background: var(--primary);
            color: #fff;
            font-weight: 700;
            font-size: 1rem;
            padding: 0.25rem 0.9rem;
            border-radius: 20px;
            margin-bottom: 0.5rem;
            letter-spacing: 0.5px;
        }

        .header h1 {
            font-size: 1.35rem;
            font-weight: 700;
            color: var(--text-dark);
            margin-bottom: 0.25rem;
        }

        .header p {
            font-size: 0.875rem;
            color: var(--text-muted);
        }

        .content {
            padding: 1.75rem 1.5rem;
        }

        .info-box {
            background: #f0f9ff;
            border: 1px solid #bae6fd;
            border-radius: 8px;
            padding: 1rem;
            margin-bottom: 1.5rem;
        }

        .info-title {
            font-weight: 700;
            color: #0369a1;
            margin-bottom: 0.5rem;
            font-size: 0.95rem;
        }

        .info-text {
            font-size: 0.85rem;
            color: #0c4a6e;
            line-height: 1.5;
        }

        .migration-list {
            background: #f8fafc;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1rem;
            margin-bottom: 1.5rem;
            max-height: 180px;
            overflow-y: auto;
        }

        .migration-item {
            font-family: monospace;
            font-size: 0.8rem;
            color: #334155;
            padding: 0.35rem 0;
            border-bottom: 1px dashed #e2e8f0;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .migration-item:last-child {
            border-bottom: none;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #f59e0b;
        }

        .btn-primary {
            width: 100%;
            background: var(--primary);
            color: #ffffff;
            border: none;
            padding: 0.85rem;
            border-radius: 8px;
            font-weight: 700;
            font-size: 0.95rem;
            cursor: pointer;
            transition: background 0.2s ease;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
        }

        .btn-primary:hover {
            background: var(--primary-hover);
        }

        .alert-error {
            background: #fef2f2;
            border: 1px solid #fecaca;
            color: var(--danger);
            padding: 0.85rem;
            border-radius: 8px;
            margin-bottom: 1.25rem;
            font-size: 0.85rem;
        }

        .footer-note {
            text-align: center;
            font-size: 0.75rem;
            color: var(--text-muted);
            margin-top: 1.25rem;
        }
    </style>
</head>
<body>
    <div class="installer-box">
        <div class="header">
            <div class="lang-switch">
                <button type="button" class="lang-btn active" id="btn-en" onclick="setLang('en')">EN</button>
                <button type="button" class="lang-btn" id="btn-bn" onclick="setLang('bn')">বাংলা</button>
            </div>
            <div class="brand-badge">SAFA</div>
            <h1 id="title-text">System Update & Database Migration</h1>
            <p id="subtitle-text">New schema updates detected. Click below to run database migrations.</p>
        </div>

        <div class="content">
            @if(session('error'))
                <div class="alert-error">
                    ⚠️ {{ session('error') }}
                </div>
            @endif

            <div class="info-box">
                <div class="info-title" id="info-header">🛡️ Zero Data Loss Preservation Guarantee</div>
                <div class="info-text" id="info-desc">
                    Existing database tables, user records, and financial transactions will remain 100% untouched. Only new tables and missing columns will be safely added.
                </div>
            </div>

            <div style="font-weight: 600; font-size: 0.875rem; margin-bottom: 0.5rem; color: #334155;" id="pending-label">
                Pending Migrations ({{ count($pendingMigrations) }}):
            </div>

            <div class="migration-list">
                @foreach($pendingMigrations as $migration)
                    <div class="migration-item">
                        <span class="status-dot"></span>
                        <span>{{ $migration }}</span>
                    </div>
                @endforeach
            </div>

            <form action="{{ route('install.update-process') }}" method="POST" onsubmit="handleUpdateSubmit(event)">
                @csrf
                <button type="submit" class="btn-primary" id="btn-submit">
                    <span>🚀</span> <span id="btn-text">Run Database Migration Now</span>
                </button>
            </form>

            <div class="footer-note" id="footer-text">
                SAFA Account System • Multi-Layer Security Hardened
            </div>
        </div>
    </div>

    <script>
        let currentLang = 'en';
        const i18n = {
            en: {
                title: "System Update & Database Migration",
                subtitle: "New schema updates detected. Click below to run database migrations.",
                infoHeader: "🛡️ Zero Data Loss Preservation Guarantee",
                infoDesc: "Existing database tables, user records, and financial transactions will remain 100% untouched. Only new tables and missing columns will be safely added.",
                pendingLabel: "Pending Migrations ({{ count($pendingMigrations) }}):",
                btnText: "Run Database Migration Now",
                btnUpdating: "Updating Database Schema... Please wait",
                footerText: "SAFA Account System • Multi-Layer Security Hardened"
            },
            bn: {
                title: "সিস্টেম আপডেট ও ডাটাবেস মাইগ্রেশন",
                subtitle: "নতুন স্কিমা আপডেট পাওয়া গেছে। ডাটাবেস মাইগ্রেশন সম্পন্ন করতে নিচে ক্লিক করুন।",
                infoHeader: "🛡️ শতভাগ ডাটা সুরক্ষা নিশ্চয়তা",
                infoDesc: "আপনার বিদ্যমান ডাটাবেসের সকল ইউজার, লেনদেন ও হিসাব ১০০% অক্ষত থাকবে। শুধুমাত্র নতুন টেবিল ও নতুন কলামগুলো নিরাপদে যুক্ত হবে।",
                pendingLabel: "অপেক্ষমান মাইগ্রেশন সমূহ ({{ count($pendingMigrations) }} টি):",
                btnText: "ডাটাবেস মাইগ্রেশন সম্পন্ন করুন",
                btnUpdating: "ডাটাবেস আপডেট হচ্ছে... অনুগ্রহ করে অপেক্ষা করুন",
                footerText: "সাফা অ্যাকাউন্ট সিস্টেম • মাল্টি-লেয়ার সিকিউরিটি গার্ড"
            }
        };

        function setLang(lang) {
            currentLang = lang;
            document.querySelectorAll('.lang-btn').forEach(b => b.classList.remove('active'));
            document.getElementById('btn-' + lang).classList.add('active');

            const t = i18n[lang];
            document.getElementById('title-text').innerText = t.title;
            document.getElementById('subtitle-text').innerText = t.subtitle;
            document.getElementById('info-header').innerText = t.infoHeader;
            document.getElementById('info-desc').innerText = t.infoDesc;
            document.getElementById('pending-label').innerText = t.pendingLabel;
            document.getElementById('btn-text').innerText = t.btnText;
            document.getElementById('footer-text').innerText = t.footerText;
        }

        function handleUpdateSubmit(e) {
            const btn = document.getElementById('btn-submit');
            const text = document.getElementById('btn-text');
            btn.style.opacity = '0.7';
            btn.style.cursor = 'not-allowed';
            btn.disabled = true;
            text.innerText = i18n[currentLang].btnUpdating;
            e.target.submit();
        }
    </script>
</body>
</html>
