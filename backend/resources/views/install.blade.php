<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>SAFA System Installation | Setup Wizard</title>
    <link rel="icon" type="image/png" href="{{ asset('safa-logo.png') }}">
    <link rel="alternate icon" type="image/png" href="{{ asset('safa-logo.png') }}">
    <link rel="apple-touch-icon" href="{{ asset('safa-logo.png') }}">
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
            max-width: 680px;
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
        }

        .header p {
            color: var(--text-muted);
            font-size: 0.88rem;
            margin-top: 0.25rem;
        }

        /* Step Progress Bar */
        .step-progress {
            display: flex;
            background: #f8fafc;
            border-bottom: 1px solid var(--border-color);
        }

        .step-item {
            flex: 1;
            padding: 0.75rem 0.25rem;
            text-align: center;
            font-size: 0.8rem;
            font-weight: 600;
            color: var(--text-muted);
            border-bottom: 3px solid transparent;
            transition: all 0.2s ease;
            white-space: nowrap;
        }

        .step-item.active {
            color: var(--primary);
            border-bottom-color: var(--primary);
            background: #ffffff;
        }

        .content {
            padding: 1.5rem;
        }

        .step-panel {
            display: none;
        }

        .step-panel.active {
            display: block;
        }

        .section-title {
            font-size: 1rem;
            font-weight: 700;
            margin-bottom: 1rem;
            color: var(--text-dark);
        }

        /* Requirements List */
        .req-list {
            display: flex;
            flex-direction: column;
            gap: 0.65rem;
        }

        .req-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.75rem 0.9rem;
            background: var(--input-bg);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            font-size: 0.88rem;
            gap: 0.5rem;
        }

        .badge {
            padding: 0.25rem 0.65rem;
            border-radius: 20px;
            font-size: 0.78rem;
            font-weight: 600;
            white-space: nowrap;
        }

        .badge-success {
            background: #dcfce7;
            color: #15803d;
        }

        .badge-danger {
            background: #fee2e2;
            color: #b91c1c;
        }

        /* Form Inputs */
        .form-group {
            margin-bottom: 1rem;
        }

        label {
            display: block;
            font-size: 0.85rem;
            font-weight: 600;
            margin-bottom: 0.35rem;
            color: var(--text-dark);
        }

        input[type="text"], input[type="url"], input[type="number"], input[type="password"] {
            width: 100%;
            padding: 0.7rem 0.85rem;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: var(--input-bg);
            font-size: 16px;
            color: var(--text-dark);
            outline: none;
            transition: border-color 0.2s ease;
        }

        input:focus {
            border-color: var(--primary);
            background: #ffffff;
        }

        .btn-group {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-top: 1.5rem;
            padding-top: 1rem;
            border-top: 1px solid var(--border-color);
            gap: 0.75rem;
        }

        .btn {
            padding: 0.7rem 1.25rem;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.9rem;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.4rem;
            text-decoration: none;
            white-space: nowrap;
        }

        .btn-primary {
            background: var(--primary);
            color: #ffffff;
        }

        .btn-primary:hover {
            background: var(--primary-hover);
        }

        .btn-secondary {
            background: #e2e8f0;
            color: #334155;
        }

        .btn-secondary:hover {
            background: #cbd5e1;
        }

        .btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

        .alert {
            padding: 0.85rem 1rem;
            border-radius: 8px;
            margin-bottom: 1.25rem;
            font-size: 0.85rem;
            line-height: 1.4;
        }

        .alert-danger {
            background: #fee2e2;
            color: #991b1b;
            border: 1px solid #fca5a5;
        }

        .alert-success {
            background: #dcfce7;
            color: #166534;
            border: 1px solid #86efac;
        }

        .spinner {
            width: 15px;
            height: 15px;
            border: 2px solid rgba(255,255,255,0.4);
            border-radius: 50%;
            border-top-color: #fff;
            animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
            to { transform: rotate(360deg); }
        }

        /* Mobile Adjustments */
        @media (max-width: 540px) {
            body {
                padding: 0.75rem 0.5rem;
            }

            .header {
                padding: 1.5rem 1rem 1rem;
            }

            .lang-switch {
                top: 0.75rem;
                right: 0.75rem;
            }

            .content {
                padding: 1rem 0.85rem;
            }

            .step-item {
                font-size: 0.75rem;
                padding: 0.65rem 0.15rem;
            }

            .btn-group {
                flex-direction: column-reverse;
            }

            .btn {
                width: 100%;
            }

            .req-row {
                font-size: 0.82rem;
                padding: 0.65rem 0.75rem;
            }
        }
    </style>
</head>
<body>
    <div class="installer-box">
        <!-- Header -->
        <div class="header">
            <div class="lang-switch">
                <button type="button" class="lang-btn active" id="langEN" onclick="setLanguage('en')">EN</button>
                <button type="button" class="lang-btn" id="langBN" onclick="setLanguage('bn')">বাংলা</button>
            </div>
            <div style="margin-bottom: 0.75rem;">
                <img src="{{ asset('safa-logo.png') }}" alt="SAFA Logo" style="height: 60px; max-width: 100%; object-fit: contain;">
            </div>
            <span class="brand-badge">SAFA</span>
            <h1 id="txtTitle">SAFA System Installation Wizard</h1>
            <p id="txtSubTitle">Configure your server and database settings in 3 easy steps</p>
        </div>

        <!-- Step Progress Bar -->
        <div class="step-progress">
            <div class="step-item active" id="stepIndicator1">1. System Checks</div>
            <div class="step-item" id="stepIndicator2">2. Application Info</div>
            <div class="step-item" id="stepIndicator3">3. Database Setup</div>
        </div>

        <div class="content">
            @if (session('error'))
                <div class="alert alert-danger">
                    <strong>⚠️ Error:</strong> {{ session('error') }}
                </div>
            @endif

            @if ($errors->any())
                <div class="alert alert-danger">
                    <strong>⚠️ Validation Error:</strong>
                    <ul style="margin-left: 1.25rem; margin-top: 0.4rem;">
                        @foreach ($errors->all() as $err)
                            <li>{{ $err }}</li>
                        @endforeach
                    </ul>
                </div>
            @endif

            <form action="{{ route('install.process') }}" method="POST" id="installForm">
                @csrf

                <!-- STEP 1: Requirements -->
                <div class="step-panel active" id="stepPanel1">
                    <div class="section-title" id="txtStep1Title">Step 1: Server Requirements Check</div>
                    <div class="req-list">
                        @foreach ($requirements as $key => $req)
                            <div class="req-row">
                                <span>{{ $req['name'] }}</span>
                                @if ($req['satisfied'])
                                    <span class="badge badge-success">✓ {{ $req['current'] }}</span>
                                @else
                                    <span class="badge badge-danger">✗ {{ $req['current'] }}</span>
                                @endif
                            </div>
                        @endforeach
                    </div>

                    <div class="btn-group" style="justify-content: flex-end;">
                        @if ($allRequirementsMet)
                            <button type="button" class="btn btn-primary" id="btnNext1" onclick="goToStep(2)">Next: App Info →</button>
                        @else
                            <button type="button" class="btn btn-secondary" id="btnFixReq" disabled>Fix Server Requirements</button>
                        @endif
                    </div>
                </div>

                <!-- STEP 2: App Info -->
                <div class="step-panel" id="stepPanel2">
                    <div class="section-title" id="txtStep2Title">Step 2: System & Domain Configuration</div>
                    
                    <div class="form-group">
                        <label for="app_name" id="lblAppName">System Name (App Name)</label>
                        <input type="text" id="app_name" name="app_name" value="{{ old('app_name', $defaults['app_name']) }}" required placeholder="e.g. SAFA System">
                    </div>

                    <div class="form-group">
                        <label for="app_url" id="lblAppUrl">System URL (App URL)</label>
                        <input type="url" id="app_url" name="app_url" value="{{ old('app_url', $defaults['app_url']) }}" required placeholder="https://safa.masarax.com">
                    </div>

                    <div class="btn-group">
                        <button type="button" class="btn btn-secondary" id="btnPrev2" onclick="goToStep(1)">← Previous</button>
                        <button type="button" class="btn btn-primary" id="btnNext2" onclick="goToStep(3)">Next: Database →</button>
                    </div>
                </div>

                <!-- STEP 3: Database Setup -->
                <div class="step-panel" id="stepPanel3">
                    <div class="section-title" id="txtStep3Title">Step 3: Database Connection Settings</div>

                    <div class="form-group">
                        <label for="db_host" id="lblDbHost">Database Host</label>
                        <input type="text" id="db_host" name="db_host" value="{{ old('db_host', $defaults['db_host']) }}" required placeholder="localhost">
                    </div>

                    <div class="form-group">
                        <label for="db_name" id="lblDbName">Database Name</label>
                        <input type="text" id="db_name" name="db_name" value="{{ old('db_name', $defaults['db_name']) }}" required placeholder="cpaneluser_safadb">
                    </div>

                    <div class="form-group">
                        <label for="db_user" id="lblDbUser">Database Username</label>
                        <input type="text" id="db_user" name="db_user" value="{{ old('db_user', $defaults['db_user']) }}" required placeholder="cpaneluser_safauser">
                    </div>

                    <div class="form-group">
                        <label for="db_pass" id="lblDbPass">Database Password</label>
                        <input type="password" id="db_pass" name="db_pass" value="{{ old('db_pass', $defaults['db_pass']) }}" placeholder="Enter MySQL Password">
                    </div>

                    <input type="hidden" id="db_port" name="db_port" value="3306">

                    <div class="form-group">
                        <button type="button" class="btn btn-secondary" id="btnTestDb" onclick="testConnection()" style="width: 100%;">
                            🔌 Test DB Connection
                        </button>
                        <div id="db-test-result" style="display: none; margin-top: 0.5rem;"></div>
                    </div>

                    <div class="btn-group">
                        <button type="button" class="btn btn-secondary" id="btnPrev3" onclick="goToStep(2)">← Previous</button>
                        <button type="submit" class="btn btn-primary" id="btnSubmit">
                            🚀 Complete Installation
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <script>
        let currentStep = 1;
        let currentLang = localStorage.getItem('safa_install_lang') || 'en';

        const i18n = {
            en: {
                title: "SAFA System Installation Wizard",
                subtitle: "Configure your server and database settings in 3 easy steps",
                step1Indicator: "1. System Checks",
                step2Indicator: "2. Application Info",
                step3Indicator: "3. Database Setup",
                step1Title: "Step 1: Server Requirements Check",
                btnNext1: "Next: App Info →",
                btnFixReq: "Fix Server Requirements",
                step2Title: "Step 2: System & Domain Configuration",
                lblAppName: "System Name (App Name)",
                lblAppUrl: "System URL (App URL)",
                btnPrev2: "← Previous",
                btnNext2: "Next: Database →",
                step3Title: "Step 3: Database Connection Settings",
                lblDbHost: "Database Host",
                lblDbName: "Database Name",
                lblDbUser: "Database Username",
                lblDbPass: "Database Password",
                btnTestDb: "🔌 Test DB Connection",
                btnPrev3: "← Previous",
                btnSubmit: "🚀 Complete Installation",
                testingConn: "Testing Connection...",
                installingSys: "Installing System & Running Migrations..."
            },
            bn: {
                title: "সাফা সিস্টেম ইনস্টলেশন উইজার্ড",
                subtitle: "সহজ ৩ টি ধাপে আপনার সার্ভার ও ডাটাবেস কনফিগার করুন",
                step1Indicator: "১. সিস্টেম চেক",
                step2Indicator: "২. ওয়েবসাইটের তথ্য",
                step3Indicator: "৩. ডাটাবেস সংযোগ",
                step1Title: "ধাপ ১: সার্ভার প্রয়োজনীয়তা পরীক্ষা",
                btnNext1: "পরবর্তী ধাপ (ওয়েবসাইট তথ্য) →",
                btnFixReq: "সার্ভার পারমিশন ঠিক করুন",
                step2Title: "ধাপ ২: ওয়েবসাইটের নাম ও ইউআরএল",
                lblAppName: "ওয়েবসাইটের নাম (System Name)",
                lblAppUrl: "ওয়েবসাইট ইউআরএল (App URL)",
                btnPrev2: "← আগের ধাপ",
                btnNext2: "পরবর্তী ধাপ (ডাটাবেস সেটিংস) →",
                step3Title: "ধাপ ৩: cPanel ডাটাবেস সংযোগ সেটিংস",
                lblDbHost: "ডাটাবেস হোস্ট (DB Host)",
                lblDbName: "ডাটাবেসের নাম (Database Name)",
                lblDbUser: "ডাটাবেস ইউজার নেম (Database User)",
                lblDbPass: "ডাটাবেস পাসওয়ার্ড (Database Password)",
                btnTestDb: "🔌 ডাটাবেস কানেকশন পরীক্ষা করুন",
                btnPrev3: "← আগের ধাপ",
                btnSubmit: "🚀 ইনস্টলেশন সম্পন্ন করুন",
                testingConn: "কানেকশন পরীক্ষা করা হচ্ছে...",
                installingSys: "ইনস্টলেশন চলছে..."
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
            document.getElementById('stepIndicator1').innerText = t.step1Indicator;
            document.getElementById('stepIndicator2').innerText = t.step2Indicator;
            document.getElementById('stepIndicator3').innerText = t.step3Indicator;
            document.getElementById('txtStep1Title').innerText = t.step1Title;
            
            const btnNext1 = document.getElementById('btnNext1');
            if (btnNext1) btnNext1.innerText = t.btnNext1;

            document.getElementById('txtStep2Title').innerText = t.step2Title;
            document.getElementById('lblAppName').innerText = t.lblAppName;
            document.getElementById('lblAppUrl').innerText = t.lblAppUrl;
            document.getElementById('btnPrev2').innerText = t.btnPrev2;
            document.getElementById('btnNext2').innerText = t.btnNext2;

            document.getElementById('txtStep3Title').innerText = t.step3Title;
            document.getElementById('lblDbHost').innerText = t.lblDbHost;
            document.getElementById('lblDbName').innerText = t.lblDbName;
            document.getElementById('lblDbUser').innerText = t.lblDbUser;
            document.getElementById('lblDbPass').innerText = t.lblDbPass;
            document.getElementById('btnTestDb').innerText = t.btnTestDb;
            document.getElementById('btnPrev3').innerText = t.btnPrev3;
            document.getElementById('btnSubmit').innerText = t.btnSubmit;
        }

        function goToStep(step) {
            document.getElementById(`stepPanel${currentStep}`).classList.remove('active');
            document.getElementById(`stepIndicator${currentStep}`).classList.remove('active');

            currentStep = step;

            document.getElementById(`stepPanel${currentStep}`).classList.add('active');
            document.getElementById(`stepIndicator${currentStep}`).classList.add('active');
        }

        async function testConnection() {
            const btn = document.getElementById('btnTestDb');
            const resDiv = document.getElementById('db-test-result');
            const t = i18n[currentLang];

            btn.disabled = true;
            btn.innerHTML = `<div class="spinner"></div> ${t.testingConn}`;
            resDiv.style.display = 'none';

            const payload = {
                db_host: document.getElementById('db_host').value,
                db_port: document.getElementById('db_port').value,
                db_name: document.getElementById('db_name').value,
                db_user: document.getElementById('db_user').value,
                db_pass: document.getElementById('db_pass').value,
            };

            try {
                const response = await fetch('{{ route("install.test-db") }}', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]').getAttribute('content'),
                        'Accept': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });

                const data = await response.json();
                resDiv.style.display = 'block';

                if (response.ok && data.success) {
                    resDiv.className = 'alert alert-success';
                    resDiv.innerHTML = '✅ ' + data.message;
                } else {
                    resDiv.className = 'alert alert-danger';
                    resDiv.innerHTML = '❌ ' + (data.message || 'Database connection failed!');
                }
            } catch (err) {
                resDiv.style.display = 'block';
                resDiv.className = 'alert alert-danger';
                resDiv.innerHTML = '❌ Connection test error: ' + err.message;
            } finally {
                btn.disabled = false;
                btn.innerHTML = t.btnTestDb;
            }
        }

        document.getElementById('installForm').addEventListener('submit', function() {
            const submitBtn = document.getElementById('btnSubmit');
            const t = i18n[currentLang];
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = `<div class="spinner"></div> ${t.installingSys}`;
            }
        });

        // Initialize language
        setLanguage(currentLang);
    </script>
</body>
</html>
