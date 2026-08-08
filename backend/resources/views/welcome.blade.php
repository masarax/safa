<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SAFA System - Homepage & System Status</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0f172a;
            --card-bg: #1e293b;
            --primary: #3b82f6;
            --primary-hover: #2563eb;
            --success: #10b981;
            --warning: #f59e0b;
            --danger: #ef4444;
            --text-light: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #334155;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-light);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 1.5rem 0.75rem;
        }

        .container {
            width: 100%;
            max-width: 540px;
        }

        .card {
            background: var(--card-bg);
            border-radius: 16px;
            padding: 2.25rem 2rem;
            text-align: center;
            border: 1px solid var(--border-color);
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
        }

        .brand-badge {
            display: inline-block;
            background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
            color: #fff;
            font-weight: 800;
            font-size: 1.2rem;
            padding: 0.4rem 1.4rem;
            border-radius: 30px;
            margin-bottom: 1.25rem;
            letter-spacing: 1px;
            box-shadow: 0 4px 14px rgba(59, 130, 246, 0.4);
        }

        h1 {
            font-size: 1.5rem;
            font-weight: 700;
            color: var(--text-light);
            margin-bottom: 0.5rem;
        }

        .subtitle {
            font-size: 0.92rem;
            color: var(--text-muted);
            margin-bottom: 1.5rem;
        }

        .update-box {
            background: rgba(245, 158, 11, 0.1);
            border: 1px solid rgba(245, 158, 11, 0.3);
            border-radius: 12px;
            padding: 1.5rem;
            margin-top: 1.25rem;
            text-align: left;
        }

        .update-title {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 1.05rem;
            font-weight: 700;
            color: var(--warning);
            margin-bottom: 0.5rem;
        }

        .update-desc {
            font-size: 0.88rem;
            color: var(--text-muted);
            line-height: 1.5;
            margin-bottom: 1rem;
        }

        .migration-list {
            background: rgba(15, 23, 42, 0.6);
            border-radius: 8px;
            padding: 0.75rem 1rem;
            max-height: 140px;
            overflow-y: auto;
            margin-bottom: 1.25rem;
            border: 1px solid var(--border-color);
        }

        .migration-item {
            font-family: monospace;
            font-size: 0.82rem;
            color: #cbd5e1;
            padding: 0.25rem 0;
            display: flex;
            align-items: center;
            gap: 0.4rem;
        }

        .btn-update {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            width: 100%;
            padding: 0.85rem 1.5rem;
            background: linear-gradient(135deg, #10b981 0%, #059669 100%);
            color: #fff;
            border: none;
            border-radius: 10px;
            font-size: 0.95rem;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 4px 14px rgba(16, 185, 129, 0.4);
            text-decoration: none;
        }

        .btn-update:hover {
            transform: translateY(-1px);
            box-shadow: 0 6px 20px rgba(16, 185, 129, 0.6);
        }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.45rem 1.1rem;
            border-radius: 30px;
            font-size: 0.88rem;
            font-weight: 600;
            background: rgba(16, 185, 129, 0.15);
            color: var(--success);
            border: 1px solid rgba(16, 185, 129, 0.3);
            margin-top: 0.5rem;
        }

        .status-dot {
            width: 10px;
            height: 10px;
            background-color: var(--success);
            border-radius: 50%;
            box-shadow: 0 0 10px var(--success);
        }

        .alert-success {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid rgba(16, 185, 129, 0.4);
            color: var(--success);
            padding: 0.85rem;
            border-radius: 10px;
            font-size: 0.9rem;
            font-weight: 600;
            margin-bottom: 1.25rem;
        }

        .alert-error {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.4);
            color: var(--danger);
            padding: 0.85rem;
            border-radius: 10px;
            font-size: 0.9rem;
            font-weight: 600;
            margin-bottom: 1.25rem;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="brand-badge">SAFA API</div>
            <h1>SAFA Backend Synchronization Server</h1>
            <p class="subtitle">Real-time API & Database Manager</p>

            @if(session('success'))
                <div class="alert-success">
                    ✅ {{ session('success') }}
                </div>
            @endif

            @if(session('error'))
                <div class="alert-error">
                    ⚠️ {{ session('error') }}
                </div>
            @endif

            @php
                $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';
                $pendingMigrations = \App\Http\Controllers\InstallerController::getPendingMigrations();
            @endphp

            @if (!$isInstalled)
                <script>window.location.href = "{{ url('/install') }}";</script>
            @elseif (!empty($pendingMigrations))
                <div class="update-box">
                    <div class="update-title">
                        <span>⚠️</span>
                        <span>Database Update Needed / ডাটাবেস আপডেট প্রয়োজন</span>
                    </div>
                    <p class="update-desc">
                        New database tables or schema updates detected. Click below to execute updates safely with <strong>zero data loss</strong>.
                        <br>
                        (নতুন কলাম বা টেবিল যুক্ত হয়েছে। পূর্বের ডাটা হারানো ছাড়াই ডাটাবেস আপডেট করুন।)
                    </p>

                    <div class="migration-list">
                        @foreach ($pendingMigrations as $migration)
                            <div class="migration-item">
                                <span>📄</span> {{ $migration }}
                            </div>
                        @endforeach
                    </div>

                    <form action="{{ route('install.update-process') }}" method="POST">
                        @csrf
                        <button type="submit" class="btn-update">
                            <span>⚡</span> Update Database Now (ডাটাবেস আপডেট করুন)
                        </button>
                    </form>
                </div>
            @else
                <div class="status-pill">
                    <span class="status-dot"></span>
                    System Operational & Database Up-to-Date
                </div>
            @endif
        </div>
    </div>
</body>
</html>
