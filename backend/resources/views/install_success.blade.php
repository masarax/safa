<!DOCTYPE html>
<html lang="bn">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Installation Successful | সাফা ইনস্টলেশন সম্পন্ন</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #f1f5f9;
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
            font-family: 'Hind Siliguri', 'Inter', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-dark);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 2rem 1rem;
        }

        .container {
            width: 100%;
            max-width: 620px;
        }

        .success-card {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 2.5rem 2rem;
            text-align: center;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
            border: 1px solid var(--border-color);
        }

        .icon-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 64px;
            height: 64px;
            background: #dcfce7;
            border: 2px solid #86efac;
            color: var(--success);
            border-radius: 50%;
            font-size: 2rem;
            margin-bottom: 1rem;
        }

        h1 {
            font-size: 1.6rem;
            font-weight: 700;
            margin-bottom: 0.4rem;
            color: var(--text-dark);
        }

        p.subtitle {
            color: var(--text-muted);
            font-size: 0.95rem;
            margin-bottom: 1.5rem;
        }

        .info-box {
            background: #f8fafc;
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 1.25rem;
            text-align: left;
            margin-bottom: 1.5rem;
        }

        .info-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.6rem 0;
            border-bottom: 1px solid var(--border-color);
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
            font-size: 0.9rem;
            font-weight: 600;
            color: var(--text-dark);
            font-family: monospace;
        }

        .url-box {
            background: #eff6ff;
            border: 1px solid #bfdbfe;
            padding: 0.75rem 1rem;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 0.5rem;
        }

        .url-text {
            font-family: monospace;
            font-size: 0.95rem;
            color: var(--primary);
            font-weight: 600;
        }

        .btn-copy {
            background: #ffffff;
            border: 1px solid #bfdbfe;
            color: var(--primary);
            padding: 0.4rem 0.8rem;
            border-radius: 6px;
            font-size: 0.8rem;
            cursor: pointer;
            font-weight: 600;
            transition: all 0.2s ease;
        }

        .btn-copy:hover {
            background: #dbeafe;
        }

        .actions-group {
            display: flex;
            gap: 1rem;
            justify-content: center;
        }

        .btn {
            padding: 0.75rem 1.5rem;
            border-radius: 8px;
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
    </style>
</head>
<body>
    <div class="container">
        <div class="success-card">
            <div class="icon-badge">✓</div>
            <h1>ইনস্টলেশন সফলভাবে সম্পন্ন হয়েছে!</h1>
            <p class="subtitle">সাফা ব্যাকএন্ড সিস্টেম ও ডাটাবেস প্রস্তুত হয়েছে</p>

            <div class="info-box">
                <div class="info-row">
                    <span class="info-label">এনভায়রনমেন্ট স্ট্যাটাস</span>
                    <span class="info-value" style="color: var(--success);">Production (APP_INSTALLED=true)</span>
                </div>
                <div class="info-row">
                    <span class="info-label">ডাটাবেস মাইগ্রেশন</span>
                    <span class="info-value" style="color: var(--success);">সম্পূর্ণ হয়েছে</span>
                </div>
                <div class="info-row">
                    <span class="info-label">সিকিউরিটি লক ফাইল</span>
                    <span class="info-value">storage/installed</span>
                </div>
                <div style="margin-top: 0.75rem;">
                    <span class="info-label">API Base Endpoint:</span>
                    <div class="url-box">
                        <span class="url-text" id="apiUrlText">{{ $apiUrl ?? 'https://safa.masarax.com/api/' }}</span>
                        <button class="btn-copy" onclick="copyApiUrl()">📋 Copy</button>
                    </div>
                </div>
            </div>

            <div class="actions-group">
                <a href="{{ url('/') }}" class="btn btn-primary">
                    🏠 হোমপেজে যান
                </a>
                <a href="{{ url('/up') }}" target="_blank" class="btn btn-secondary">
                    💚 সার্ভার স্ট্যাটাস চেক
                </a>
            </div>
        </div>
    </div>

    <script>
        function copyApiUrl() {
            const urlText = document.getElementById('apiUrlText').innerText;
            navigator.clipboard.writeText(urlText).then(() => {
                const btn = document.querySelector('.btn-copy');
                btn.innerText = '✅ কপি হয়েছে!';
                setTimeout(() => { btn.innerText = '📋 Copy'; }, 2000);
            });
        }
    </script>
</body>
</html>
