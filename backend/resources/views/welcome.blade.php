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
            border-radius: 12px;
            padding: 2rem;
            text-align: center;
            border: 1px solid var(--border-color);
        }

        .brand-badge {
            display: inline-block;
            background: var(--primary);
            color: #fff;
            font-weight: 700;
            font-size: 1.1rem;
            padding: 0.3rem 1.1rem;
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
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="brand-badge">SAFA</div>
            <h1>SAFA System Online</h1>

            @php
                $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';
            @endphp

            @if ($isInstalled)
                <div class="status-pill">
                    <span class="status-dot"></span>
                    Operational
                </div>
            @else
                <script>window.location.href = "{{ url('/install') }}";</script>
            @endif
        </div>
    </div>
</body>
</html>
