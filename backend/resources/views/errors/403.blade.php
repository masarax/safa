<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>403 - Forbidden | SAFA</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #f8fafc;
            --card-bg: #ffffff;
            --primary: #2563eb;
            --text-dark: #0f172a;
            --text-muted: #64748b;
            --border-color: #e2e8f0;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
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
        .card {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 2.5rem 2rem;
            text-align: center;
            border: 1px solid var(--border-color);
            max-width: 480px;
            width: 100%;
        }
        .brand-badge {
            display: inline-block;
            background: var(--primary);
            color: #fff;
            font-weight: 700;
            font-size: 1rem;
            padding: 0.25rem 0.9rem;
            border-radius: 20px;
            margin-bottom: 1.25rem;
        }
        h1 { font-size: 1.5rem; font-weight: 700; color: var(--text-dark); margin-bottom: 0.5rem; }
        p { color: var(--text-muted); font-size: 0.9rem; margin-bottom: 1.5rem; line-height: 1.5; }
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 0.7rem 1.25rem;
            background: var(--primary);
            color: #ffffff;
            text-decoration: none;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.9rem;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="brand-badge">SAFA System</div>
        <h1>403 - Access Forbidden</h1>
        <p>You do not have permission to access this resource.</p>
        <a href="{{ url('/') }}" class="btn">Return Home</a>
    </div>
</body>
</html>
