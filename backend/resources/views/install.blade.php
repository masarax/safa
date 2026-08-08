<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>SAFA Setup Wizard | সাফা ব্যাকএন্ড ইনস্টলেশন</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #090d16;
            --card-bg: rgba(18, 25, 41, 0.7);
            --card-border: rgba(255, 255, 255, 0.08);
            --accent-blue: #38bdf8;
            --accent-indigo: #6366f1;
            --accent-green: #10b981;
            --accent-red: #ef4444;
            --text-main: #f8fafc;
            --text-sub: #94a3b8;
            --input-bg: rgba(15, 23, 42, 0.6);
            --input-border: rgba(255, 255, 255, 0.12);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Plus Jakarta Sans', 'Hind Siliguri', sans-serif;
            background-color: var(--bg-dark);
            background-image: 
                radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.15) 0px, transparent 50%),
                radial-gradient(at 100% 100%, rgba(56, 189, 248, 0.15) 0px, transparent 50%);
            background-attachment: fixed;
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 2rem 1rem;
        }

        .container {
            width: 100%;
            max-width: 860px;
        }

        .header-card {
            background: var(--card-bg);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 2rem;
            text-align: center;
            margin-bottom: 1.5rem;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
        }

        .logo-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 56px;
            height: 56px;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-indigo));
            border-radius: 14px;
            margin-bottom: 1rem;
            font-weight: 800;
            font-size: 1.5rem;
            color: #fff;
            box-shadow: 0 0 20px rgba(56, 189, 248, 0.4);
        }

        .header-card h1 {
            font-size: 1.75rem;
            font-weight: 700;
            margin-bottom: 0.25rem;
            background: linear-gradient(to right, #ffffff, #cbd5e1);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .header-card p {
            color: var(--text-sub);
            font-size: 0.95rem;
        }

        .main-card {
            background: var(--card-bg);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 2rem;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
        }

        .section-title {
            font-size: 1.1rem;
            font-weight: 600;
            margin-bottom: 1rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
            color: #e2e8f0;
            border-bottom: 1px solid var(--card-border);
            padding-bottom: 0.5rem;
        }

        .section-title span.step-num {
            background: rgba(56, 189, 248, 0.15);
            color: var(--accent-blue);
            width: 28px;
            height: 28px;
            border-radius: 8px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 0.85rem;
            font-weight: 700;
        }

        .requirements-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 0.75rem;
            margin-bottom: 2rem;
        }

        .req-item {
            background: rgba(15, 23, 42, 0.4);
            border: 1px solid var(--card-border);
            padding: 0.85rem 1rem;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .req-name {
            font-size: 0.85rem;
            font-weight: 500;
        }

        .req-badge {
            font-size: 0.75rem;
            padding: 0.2rem 0.6rem;
            border-radius: 20px;
            font-weight: 600;
        }

        .badge-success {
            background: rgba(16, 185, 129, 0.15);
            color: #34d399;
            border: 1px solid rgba(16, 185, 129, 0.3);
        }

        .badge-danger {
            background: rgba(239, 68, 68, 0.15);
            color: #f87171;
            border: 1px solid rgba(239, 68, 68, 0.3);
        }

        .form-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1.25rem;
            margin-bottom: 1.5rem;
        }

        @media (max-width: 640px) {
            .form-grid {
                grid-template-columns: 1fr;
            }
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 0.4rem;
        }

        .form-group.full-width {
            grid-column: 1 / -1;
        }

        label {
            font-size: 0.85rem;
            font-weight: 600;
            color: #cbd5e1;
        }

        label small {
            color: var(--text-sub);
            font-weight: 400;
            font-size: 0.75rem;
            margin-left: 0.25rem;
        }

        input[type="text"], input[type="password"], input[type="number"], input[type="url"] {
            background: var(--input-bg);
            border: 1px solid var(--input-border);
            color: #fff;
            padding: 0.7rem 0.9rem;
            border-radius: 10px;
            font-size: 0.9rem;
            outline: none;
            transition: all 0.2s ease;
        }

        input:focus {
            border-color: var(--accent-blue);
            box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15);
        }

        .input-with-btn {
            display: flex;
            gap: 0.5rem;
        }

        .input-with-btn input {
            flex: 1;
        }

        .btn {
            padding: 0.7rem 1.25rem;
            border-radius: 10px;
            font-weight: 600;
            font-size: 0.9rem;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            text-decoration: none;
        }

        .btn-primary {
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-indigo));
            color: #fff;
            box-shadow: 0 4px 15px rgba(56, 189, 248, 0.3);
        }

        .btn-primary:hover {
            opacity: 0.92;
            transform: translateY(-1px);
        }

        .btn-secondary {
            background: rgba(255, 255, 255, 0.08);
            color: #e2e8f0;
            border: 1px solid var(--card-border);
        }

        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.15);
        }

        .btn-full {
            width: 100%;
            padding: 0.85rem;
            font-size: 1rem;
        }

        .alert {
            padding: 1rem;
            border-radius: 10px;
            margin-bottom: 1.5rem;
            font-size: 0.9rem;
            line-height: 1.4;
        }

        .alert-danger {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #fca5a5;
        }

        .alert-success {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid rgba(16, 185, 129, 0.3);
            color: #6ee7b7;
        }

        #db-test-result {
            margin-top: 0.5rem;
            font-size: 0.85rem;
            display: none;
        }

        .spinner {
            width: 16px;
            height: 16px;
            border: 2px solid rgba(255,255,255,0.3);
            border-radius: 50%;
            border-top-color: #fff;
            animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
            to { transform: rotate(360deg); }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header-card">
            <div class="logo-badge">SAFA</div>
            <h1>SAFA Backend Installation Wizard</h1>
            <p>সাফা ব্যাকএন্ড সিস্টেম ইনস্টলেশন ও সেটআপ উইজার্ড</p>
        </div>

        <!-- Main Card -->
        <div class="main-card">
            @if (session('error'))
                <div class="alert alert-danger">
                    <strong>⚠️ Error / ত্রুটি:</strong> {{ session('error') }}
                </div>
            @endif

            @if ($errors->any())
                <div class="alert alert-danger">
                    <strong>⚠️ Please fix input errors / অনুগ্রহ করে ইনপুট সমাধান করুন:</strong>
                    <ul style="margin-left: 1.25rem; margin-top: 0.4rem;">
                        @foreach ($errors->all() as $err)
                            <li>{{ $err }}</li>
                        @endforeach
                    </ul>
                </div>
            @endif

            <!-- Step 1: System Requirements -->
            <div class="section-title">
                <span class="step-num">1</span>
                System Environment Checks / সিস্টেম প্রয়োজনীয়তা
            </div>
            <div class="requirements-grid">
                @foreach ($requirements as $key => $req)
                    <div class="req-item">
                        <span class="req-name">{{ $req['name'] }}</span>
                        @if ($req['satisfied'])
                            <span class="req-badge badge-success">✓ {{ $req['current'] }}</span>
                        @else
                            <span class="req-badge badge-danger">✗ {{ $req['current'] }}</span>
                        @endif
                    </div>
                @endforeach
            </div>

            <!-- Form -->
            <form action="{{ route('install.process') }}" method="POST" id="installForm">
                @csrf

                <!-- Step 2: Application Configuration -->
                <div class="section-title">
                    <span class="step-num">2</span>
                    Application Configuration / অ্যাপ্লিকেশন কনফিগারেশন
                </div>
                <div class="form-grid">
                    <div class="form-group">
                        <label for="app_name">App Name / সিস্টেমের নাম <small>(Required)</small></label>
                        <input type="text" id="app_name" name="app_name" value="{{ old('app_name', $defaults['app_name']) }}" required placeholder="e.g. SAFA Backend">
                    </div>
                    <div class="form-group">
                        <label for="app_url">App URL / অ্যাপ্লিকেশন ইউআরএল <small>(Required)</small></label>
                        <input type="url" id="app_url" name="app_url" value="{{ old('app_url', $defaults['app_url']) }}" required placeholder="https://safa.masarax.com">
                    </div>
                </div>

                <!-- Step 3: Database Settings -->
                <div class="section-title">
                    <span class="step-num">3</span>
                    Database Settings / ডাটাবেস সেটিংস
                </div>
                <div class="form-grid">
                    <div class="form-group">
                        <label for="db_host">Database Host / হোস্ট <small>(Required)</small></label>
                        <input type="text" id="db_host" name="db_host" value="{{ old('db_host', $defaults['db_host']) }}" required placeholder="127.0.0.1">
                    </div>
                    <div class="form-group">
                        <label for="db_port">Database Port / পোর্ট <small>(Required)</small></label>
                        <input type="number" id="db_port" name="db_port" value="{{ old('db_port', $defaults['db_port']) }}" required placeholder="3306">
                    </div>
                    <div class="form-group">
                        <label for="db_name">Database Name / ডাটাবেসের নাম <small>(Required)</small></label>
                        <input type="text" id="db_name" name="db_name" value="{{ old('db_name', $defaults['db_name']) }}" required placeholder="safa">
                    </div>
                    <div class="form-group">
                        <label for="db_user">Database Username / ইউজার নাম <small>(Required)</small></label>
                        <input type="text" id="db_user" name="db_user" value="{{ old('db_user', $defaults['db_user']) }}" required placeholder="root">
                    </div>
                    <div class="form-group full-width">
                        <label for="db_pass">Database Password / পাসওয়ার্ড <small>(Optional)</small></label>
                        <input type="password" id="db_pass" name="db_pass" value="{{ old('db_pass', $defaults['db_pass']) }}" placeholder="Enter MySQL Password">
                    </div>
                    <div class="form-group full-width">
                        <button type="button" class="btn btn-secondary" id="btnTestDb" onclick="testConnection()">
                            🔌 Test DB Connection / ডাটাবেস সংযোগ পরীক্ষা করুন
                        </button>
                        <div id="db-test-result"></div>
                    </div>
                </div>

                <!-- Step 4: API Security Keys -->
                <div class="section-title">
                    <span class="step-num">4</span>
                    API Security Keys / সিকিউরিটি চাবি
                </div>
                <div class="form-grid">
                    <div class="form-group">
                        <label for="api_key">SAFA API Key <small>(Required)</small></label>
                        <div class="input-with-btn">
                            <input type="text" id="api_key" name="api_key" value="{{ old('api_key', $defaults['api_key']) }}" required>
                            <button type="button" class="btn btn-secondary" onclick="generateKey('api_key')">⚡ Key</button>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="api_secret">SAFA API Secret <small>(Required)</small></label>
                        <div class="input-with-btn">
                            <input type="text" id="api_secret" name="api_secret" value="{{ old('api_secret', $defaults['api_secret']) }}" required>
                            <button type="button" class="btn btn-secondary" onclick="generateKey('api_secret')">⚡ Secret</button>
                        </div>
                    </div>
                </div>

                <!-- Submit Button -->
                <div style="margin-top: 2rem;">
                    @if ($allRequirementsMet)
                        <button type="submit" class="btn btn-primary btn-full" id="btnSubmit">
                            🚀 Complete Setup & Run Migrations / ইনস্টল সম্পন্ন করুন
                        </button>
                    @else
                        <button type="button" class="btn btn-secondary btn-full" disabled style="opacity: 0.5; cursor: not-allowed;">
                            ⚠️ Environment Requirements Not Met / প্রয়োজনীয়তা অপূর্ণ
                        </button>
                    @endif
                </div>
            </form>
        </div>
    </div>

    <script>
        function generateRandomHex(length = 32) {
            const arr = new Uint8Array(length / 2);
            window.crypto.getRandomValues(arr);
            return Array.from(arr, byte => byte.toString(16).padStart(2, '0')).join('');
        }

        function generateKey(fieldId) {
            document.getElementById(fieldId).value = generateRandomHex(32);
        }

        async function testConnection() {
            const btn = document.getElementById('btnTestDb');
            const resDiv = document.getElementById('db-test-result');
            
            btn.disabled = true;
            btn.innerHTML = '<div class="spinner"></div> Testing Connection... / পরীক্ষা করা হচ্ছে...';
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
                    resDiv.style.marginTop = '0.5rem';
                    resDiv.innerHTML = '✅ ' + data.message;
                } else {
                    resDiv.className = 'alert alert-danger';
                    resDiv.style.marginTop = '0.5rem';
                    resDiv.innerHTML = '❌ ' + (data.message || 'Database connection failed!');
                }
            } catch (err) {
                resDiv.style.display = 'block';
                resDiv.className = 'alert alert-danger';
                resDiv.style.marginTop = '0.5rem';
                resDiv.innerHTML = '❌ Network or server error during connection check: ' + err.message;
            } finally {
                btn.disabled = false;
                btn.innerHTML = '🔌 Test DB Connection / ডাটাবেস সংযোগ পরীক্ষা করুন';
            }
        }

        document.getElementById('installForm').addEventListener('submit', function() {
            const submitBtn = document.getElementById('btnSubmit');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<div class="spinner"></div> Installing System & Running Migrations... / ইনস্টলেশন চলছে...';
            }
        });
    </script>
</body>
</html>
