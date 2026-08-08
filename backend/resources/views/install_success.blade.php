<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Installation Successful | সাফা ইনস্টলেশন সম্পন্ন</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #090d16;
            --card-bg: rgba(18, 25, 41, 0.7);
            --card-border: rgba(255, 255, 255, 0.08);
            --accent-green: #10b981;
            --accent-blue: #38bdf8;
            --accent-indigo: #6366f1;
            --text-main: #f8fafc;
            --text-sub: #94a3b8;
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
                radial-gradient(at 50% 0%, rgba(16, 185, 129, 0.15) 0px, transparent 50%),
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
            max-width: 680px;
        }

        .success-card {
            background: var(--card-bg);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--card-border);
            border-radius: 20px;
            padding: 2.5rem 2rem;
            text-align: center;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
        }

        .icon-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 72px;
            height: 72px;
            background: rgba(16, 185, 129, 0.15);
            border: 2px solid rgba(16, 185, 129, 0.4);
            color: #34d399;
            border-radius: 50%;
            font-size: 2.2rem;
            margin-bottom: 1.25rem;
            box-shadow: 0 0 25px rgba(16, 185, 129, 0.3);
        }

        h1 {
            font-size: 1.85rem;
            font-weight: 700;
            margin-bottom: 0.4rem;
            background: linear-gradient(to right, #ffffff, #a7f3d0);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        p.subtitle {
            color: var(--text-sub);
            font-size: 0.95rem;
            margin-bottom: 2rem;
        }

        .info-box {
            background: rgba(15, 23, 42, 0.5);
            border: 1px solid var(--card-border);
            border-radius: 14px;
            padding: 1.25rem;
            text-align: left;
            margin-bottom: 2rem;
        }

        .info-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.6rem 0;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        }

        .info-row:last-child {
            border-bottom: none;
        }

        .info-label {
            font-size: 0.85rem;
            color: var(--text-sub);
            font-weight: 500;
        }

        .info-value {
            font-size: 0.9rem;
            font-weight: 600;
            color: var(--text-main);
            font-family: monospace;
        }

        .url-box {
            background: rgba(56, 189, 248, 0.1);
            border: 1px solid rgba(56, 189, 248, 0.25);
            padding: 0.8rem 1rem;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 0.5rem;
        }

        .url-text {
            font-family: monospace;
            font-size: 0.95rem;
            color: var(--accent-blue);
            font-weight: 600;
        }

        .btn-copy {
            background: rgba(255, 255, 255, 0.1);
            border: none;
            color: #fff;
            padding: 0.4rem 0.8rem;
            border-radius: 6px;
            font-size: 0.78rem;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .btn-copy:hover {
            background: rgba(255, 255, 255, 0.2);
        }

        .actions-group {
            display: flex;
            gap: 1rem;
            justify-content: center;
        }

        @media (max-width: 480px) {
            .actions-group {
                flex-direction: column;
            }
        }

        .btn {
            padding: 0.8rem 1.5rem;
            border-radius: 12px;
            font-weight: 600;
            font-size: 0.95rem;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
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
    </style>
</head>
<body>
    <div class="container">
        <div class="success-card">
            <div class="icon-badge">✓</div>
            <h1>System Installed Successfully!</h1>
            <p class="subtitle">সাফা ব্যাকএন্ড ইনস্টলেশন ও ডাটাবেস সেটআপ সফলভাবে সম্পন্ন হয়েছে</p>

            <div class="info-box">
                <div class="info-row">
                    <span class="info-label">Environment Status / এনভায়রনমেন্ট</span>
                    <span class="info-value" style="color: #34d399;">Production Mode (APP_INSTALLED=true)</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Database Migrations / ডাটাবেস মাইগ্রেশন</span>
                    <span class="info-value" style="color: #34d399;">Completed Successfully</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Lock File / ইনস্টলেশন লক ফাইল</span>
                    <span class="info-value">storage/installed</span>
                </div>
                <div style="margin-top: 0.75rem;">
                    <span class="info-label">Backend API Base Endpoint / এপিআই বেস ইউআরএল:</span>
                    <div class="url-box">
                        <span class="url-text" id="apiUrlText">{{ $apiUrl ?? 'https://safa.masarax.com/api/' }}</span>
                        <button class="btn-copy" onclick="copyApiUrl()">📋 Copy</button>
                    </div>
                </div>
            </div>

            <div class="actions-group">
                <a href="{{ url('/') }}" class="btn btn-primary">
                    🏠 Return Home / হোমপেজে যান
                </a>
                <a href="{{ url('/up') }}" target="_blank" class="btn btn-secondary">
                    💚 Health Check / এপিআই স্ট্যাটাস
                </a>
            </div>
        </div>
    </div>

    <script>
        function copyApiUrl() {
            const urlText = document.getElementById('apiUrlText').innerText;
            navigator.clipboard.writeText(urlText).then(() => {
                const btn = document.querySelector('.btn-copy');
                btn.innerText = '✅ Copied!';
                setTimeout(() => { btn.innerText = '📋 Copy'; }, 2000);
            });
        }
    </script>
</body>
</html>
