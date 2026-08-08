<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SAFA System</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #f8fafc;
            --card-bg: #ffffff;
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --success: #16a34a;
            --warning: #d97706;
            --danger: #dc2626;
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
            font-family: 'Inter', sans-serif;
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
            max-width: 480px;
        }

        .card {
            background: var(--card-bg);
            border-radius: 16px;
            padding: 2rem;
            text-align: center;
            border: 1px solid var(--border-color);
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
        }

        .brand-badge {
            display: inline-block;
            background: var(--primary);
            color: #fff;
            font-weight: 700;
            font-size: 1.1rem;
            padding: 0.35rem 1.2rem;
            border-radius: 20px;
            margin-bottom: 1rem;
            letter-spacing: 0.5px;
        }

        h1 {
            font-size: 1.35rem;
            font-weight: 700;
            color: var(--text-dark);
            margin-bottom: 0.5rem;
        }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 0.4rem;
            padding: 0.35rem 0.85rem;
            border-radius: 20px;
            font-size: 0.82rem;
            font-weight: 600;
            background: #dcfce7;
            color: var(--success);
            border: 1px solid #86efac;
            margin-top: 0.75rem;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background-color: var(--success);
            border-radius: 50%;
        }

        .update-box {
            background: #fef3c7;
            border: 1px solid #fde68a;
            border-radius: 12px;
            padding: 1.25rem;
            margin-top: 1rem;
            text-align: left;
        }

        .update-title {
            font-size: 0.98rem;
            font-weight: 700;
            color: #92400e;
            margin-bottom: 0.4rem;
        }

        .update-desc {
            font-size: 0.84rem;
            color: #78350f;
            line-height: 1.4;
            margin-bottom: 0.85rem;
        }

        .btn-update {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.4rem;
            width: 100%;
            padding: 0.75rem 1.2rem;
            background: var(--primary);
            color: #fff;
            border: none;
            border-radius: 8px;
            font-size: 0.9rem;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s ease;
            text-decoration: none;
        }

        .btn-update:hover {
            background: var(--primary-hover);
        }

        .alert-success {
            background: #dcfce7;
            border: 1px solid #86efac;
            color: var(--success);
            padding: 0.75rem;
            border-radius: 8px;
            font-size: 0.85rem;
            font-weight: 600;
            margin-bottom: 1rem;
        }

        .alert-error {
            background: #fee2e2;
            border: 1px solid #fca5a5;
            color: var(--danger);
            padding: 0.75rem;
            border-radius: 8px;
            font-size: 0.85rem;
            font-weight: 600;
            margin-bottom: 1rem;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="brand-badge">SAFA</div>
            <h1>SAFA System Online</h1>

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
                        ⚡ Database Update Available (ডাটাবেস আপডেট প্রয়োজন)
                    </div>
                    <p class="update-desc">
                        New schema updates detected. Click below to execute database updates safely with <strong>zero data loss</strong>.
                        <br>
                        (নতুন কলাম বা টেবিল যুক্ত হয়েছে। ডাটাবেস আপডেট করুন।)
                    </p>

                    <form action="{{ route('install.update-process') }}" method="POST">
                        @csrf
                        <button type="submit" class="btn-update">
                            ⚡ Update Database Now (ডাটাবেস আপডেট করুন)
                        </button>
                    </form>
                </div>
            @else
                <div class="status-pill">
                    <span class="status-dot"></span>
                    Operational & Database Up-to-Date
                </div>
            @endif
        </div>
    </div>
</body>
</html>
