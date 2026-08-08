<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Installation Successful | SAFA Setup</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #f8fafc;
            --card-bg: #ffffff;
            --primary: #2563eb;
            --success: #16a34a;
            --text-dark: #0f172a;
            --text-muted: #64748b;
            --border-color: #e2e8f0;
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

        .container {
            width: 100%;
            max-width: 600px;
        }

        .success-card {
            background: var(--card-bg);
            border-radius: 14px;
            padding: 2.25rem 1.75rem;
            text-align: center;
            border: 1px solid var(--border-color);
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

        .icon-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 60px;
            height: 60px;
            background: #dcfce7;
            border: 2px solid #86efac;
            color: var(--success);
            border-radius: 50%;
            font-size: 1.8rem;
            margin-bottom: 1rem;
        }

        h1 {
            font-size: 1.5rem;
            font-weight: 700;
            margin-bottom: 0.4rem;
            color: var(--text-dark);
        }

        p.subtitle {
            color: var(--text-muted);
            font-size: 0.9rem;
            margin-bottom: 1.5rem;
        }

        .info-box {
            background: #f8fafc;
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 1.15rem;
            text-align: left;
            margin-bottom: 1.5rem;
        }

        .info-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.55rem 0;
            border-bottom: 1px solid var(--border-color);
            font-size: 0.88rem;
        }

        .info-row:last-child {
            border-bottom: none;
        }

        .info-label {
            font-size: 0.85rem;
            color: var(--text-muted);
            font-weight: 500;
        }

        .info-value {
            font-size: 0.88rem;
            font-weight: 600;
            color: var(--text-dark);
            font-family: monospace;
        }

        .url-box {
            background: #eff6ff;
            border: 1px solid #bfdbfe;
            padding: 0.7rem 0.9rem;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 0.5rem;
            gap: 0.5rem;
        }

        .url-text {
            font-family: monospace;
            font-size: 0.88rem;
            color: var(--primary);
            font-weight: 600;
            word-break: break-all;
        }

        .btn-copy {
            background: #ffffff;
            border: 1px solid #bfdbfe;
            color: var(--primary);
            padding: 0.35rem 0.75rem;
            border-radius: 6px;
            font-size: 0.78rem;
            cursor: pointer;
            font-weight: 600;
            transition: all 0.2s ease;
            white-space: nowrap;
        }

        .btn-copy:hover {
            background: #dbeafe;
        }

        .actions-group {
            display: flex;
            gap: 0.75rem;
            justify-content: center;
        }

        .btn {
            padding: 0.7rem 1.25rem;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.9rem;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.4rem;
        }

        .btn-primary {
            background: var(--primary);
            color: #fff;
        }

        .btn-primary:hover {
            background: #1d4ed8;
        }

        .btn-secondary {
            background: #e2e8f0;
            color: #334155;
        }

        .btn-secondary:hover {
            background: #cbd5e1;
        }

        @media (max-width: 540px) {
            .success-card {
                padding: 1.75rem 1rem;
            }

            .lang-switch {
                top: 0.75rem;
                right: 0.75rem;
            }

            .actions-group {
                flex-direction: column;
            }

            .btn {
                width: 100%;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="success-card">
            <div class="lang-switch">
                <button type="button" class="lang-btn active" id="langEN" onclick="setLanguage('en')">EN</button>
                <button type="button" class="lang-btn" id="langBN" onclick="setLanguage('bn')">বাংলা</button>
            </div>

            <div class="icon-badge">✓</div>
            <h1 id="txtTitle">Installation Completed!</h1>
            <p class="subtitle" id="txtSubTitle">SAFA backend system and database are ready for production</p>

            <div class="info-box">
                <div class="info-row">
                    <span class="info-label" id="lblEnv">Environment Status</span>
                    <span class="info-value" style="color: var(--success);" id="valEnv">Production (APP_INSTALLED=true)</span>
                </div>
                <div class="info-row">
                    <span class="info-label" id="lblMig">Database Migrations</span>
                    <span class="info-value" style="color: var(--success);" id="valMig">Completed</span>
                </div>
                <div class="info-row">
                    <span class="info-label" id="lblLock">Security Lock File</span>
                    <span class="info-value">storage/installed</span>
                </div>
                <div style="margin-top: 0.75rem;">
                    <span class="info-label" id="lblApiUrl">API Base Endpoint:</span>
                    <div class="url-box">
                        <span class="url-text" id="apiUrlText">{{ $apiUrl ?? 'https://safa.masarax.com/api/' }}</span>
                        <button class="btn-copy" onclick="copyApiUrl()">📋 Copy</button>
                    </div>
                </div>
            </div>

            <div class="actions-group">
                <a href="{{ url('/') }}" class="btn btn-primary" id="btnHome">
                    🏠 Return Home
                </a>
                <a href="{{ url('/up') }}" target="_blank" class="btn btn-secondary" id="btnStatus">
                    💚 Health Status
                </a>
            </div>
        </div>
    </div>

    <script>
        let currentLang = localStorage.getItem('safa_install_lang') || 'en';

        const i18n = {
            en: {
                title: "Installation Completed!",
                subtitle: "SAFA backend system and database are ready for production",
                lblEnv: "Environment Status",
                valEnv: "Production (APP_INSTALLED=true)",
                lblMig: "Database Migrations",
                valMig: "Completed",
                lblLock: "Security Lock File",
                lblApiUrl: "API Base Endpoint:",
                btnHome: "🏠 Return Home",
                btnStatus: "💚 Health Status"
            },
            bn: {
                title: "ইনস্টলেশন সফলভাবে সম্পন্ন হয়েছে!",
                subtitle: "সাফা ব্যাকএন্ড সিস্টেম ও ডাটাবেস প্রস্তুত হয়েছে",
                lblEnv: "এনভায়রনমেন্ট স্ট্যাটাস",
                valEnv: "Production (APP_INSTALLED=true)",
                lblMig: "ডাটাবেস মাইগ্রেশন",
                valMig: "সম্পূর্ণ হয়েছে",
                lblLock: "সিকিউরিটি লক ফাইল",
                lblApiUrl: "API Base Endpoint:",
                btnHome: "হোমপেজে যান",
                btnStatus: "সার্ভার স্ট্যাটাস চেক"
            }
        };

        function setLanguage(lang) {
            currentLang = lang;
            localStorage.setItem('safa_install_lang', lang);

            document.getElementById('langEN').classList.toggle('active', lang === 'en');
            document.getElementById('langBN').classList.toggle('active', lang === 'bn');

            const t = i18n[lang];
            document.getElementById('txtTitle').innerText = t.title;
            document.getElementById('txtSubTitle').innerText = t.subtitle;
            document.getElementById('lblEnv').innerText = t.lblEnv;
            document.getElementById('valEnv').innerText = t.valEnv;
            document.getElementById('lblMig').innerText = t.lblMig;
            document.getElementById('valMig').innerText = t.valMig;
            document.getElementById('lblLock').innerText = t.lblLock;
            document.getElementById('lblApiUrl').innerText = t.lblApiUrl;
            document.getElementById('btnHome').innerText = t.btnHome;
            document.getElementById('btnStatus').innerText = t.btnStatus;
        }

        function copyApiUrl() {
            const urlText = document.getElementById('apiUrlText').innerText;
            navigator.clipboard.writeText(urlText).then(() => {
                const btn = document.querySelector('.btn-copy');
                btn.innerText = currentLang === 'bn' ? '✅ কপি হয়েছে!' : '✅ Copied!';
                setTimeout(() => { btn.innerText = '📋 Copy'; }, 2000);
            });
        }

        setLanguage(currentLang);
    </script>
</body>
</html>
