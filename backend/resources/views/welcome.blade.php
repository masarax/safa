<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ config('app.name', 'SAFA Backend') }} | API Service</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #090d16;
            --card-bg: rgba(18, 25, 41, 0.7);
            --card-border: rgba(255, 255, 255, 0.08);
            --accent-blue: #38bdf8;
            --accent-indigo: #6366f1;
            --accent-green: #10b981;
            --text-main: #f8fafc;
            --text-sub: #94a3b8;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
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
            max-width: 640px;
        }

        .card {
            background: var(--card-bg);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--card-border);
            border-radius: 20px;
            padding: 2.5rem 2rem;
            text-align: center;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
        }

        .logo-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 64px;
            height: 64px;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-indigo));
            border-radius: 16px;
            margin-bottom: 1.25rem;
            font-weight: 800;
            font-size: 1.6rem;
            color: #fff;
            box-shadow: 0 0 25px rgba(56, 189, 248, 0.4);
        }

        h1 {
            font-size: 1.8rem;
            font-weight: 700;
            margin-bottom: 0.4rem;
        }

        p.subtitle {
            color: var(--text-sub);
            font-size: 0.95rem;
            margin-bottom: 2rem;
        }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.4rem 1rem;
            border-radius: 30px;
            font-size: 0.85rem;
            font-weight: 600;
            background: rgba(16, 185, 129, 0.15);
            color: #34d399;
            border: 1px solid rgba(16, 185, 129, 0.3);
            margin-bottom: 1.5rem;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background-color: #10b981;
            border-radius: 50%;
            box-shadow: 0 0 8px #10b981;
        }

        .details-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1rem;
            margin-bottom: 2rem;
            text-align: left;
        }

        .detail-item {
            background: rgba(15, 23, 42, 0.4);
            border: 1px solid var(--card-border);
            padding: 1rem;
            border-radius: 12px;
        }

        .detail-label {
            font-size: 0.78rem;
            color: var(--text-sub);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: 0.3rem;
        }

        .detail-value {
            font-size: 0.95rem;
            font-weight: 600;
            color: #fff;
        }

        .actions {
            display: flex;
            gap: 1rem;
            justify-content: center;
        }

        .btn {
            padding: 0.75rem 1.4rem;
            border-radius: 10px;
            font-weight: 600;
            font-size: 0.9rem;
            text-decoration: none;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
        }

        .btn-primary {
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-indigo));
            color: #fff;
        }

        .btn-primary:hover {
            opacity: 0.9;
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
        <div class="card">
            <div class="logo-badge">SAFA</div>
            <h1>{{ config('app.name', 'SAFA Backend') }}</h1>
            <p class="subtitle">RESTful Backend & Synchronization API Service</p>

            @php
                $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';
            @endphp

            @if ($isInstalled)
                <div class="status-pill">
                    <span class="status-dot"></span>
                    System Active & Installed
                </div>

                <div class="details-grid">
                    <div class="detail-item">
                        <div class="detail-label">Framework</div>
                        <div class="detail-value">Laravel v{{ app()->version() }}</div>
                    </div>
                    <div class="detail-item">
                        <div class="detail-label">PHP Engine</div>
                        <div class="detail-value">v{{ PHP_VERSION }}</div>
                    </div>
                    <div class="detail-item">
                        <div class="detail-label">API Base URL</div>
                        <div class="detail-value" style="font-family: monospace; font-size: 0.85rem;">{{ config('app.url') }}/api/</div>
                    </div>
                    <div class="detail-item">
                        <div class="detail-label">Environment</div>
                        <div class="detail-value">{{ config('app.env') }}</div>
                    </div>
                </div>

                <div class="actions">
                    <a href="{{ url('/up') }}" class="btn btn-primary" target="_blank">
                        💚 System Health
                    </a>
                </div>
            @else
                <script>window.location.href = "{{ url('/install') }}";</script>
                <div class="actions">
                    <a href="{{ url('/install') }}" class="btn btn-primary">
                        🚀 Proceed to Installation Wizard
                    </a>
                </div>
            @endif
        </div>
    </div>
</body>
</html>
