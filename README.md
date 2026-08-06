# SAFA (সাফা) - Hundi & Multi-Currency Account Management System

## Project Overview
SAFA is an offline-first enterprise financial accounting & Hundi (Hawala) multi-currency ledger application built with **Android (Kotlin, Jetpack Compose, Encrypted Room/SQLCipher)** and **Laravel 11 Backend (REST API, HMAC Authentication, SQLite/PostgreSQL)**.

---

## System Architecture

```
                 +---------------------------------------+
                 |       Android Mobile App (SAFA)       |
                 |  - Jetpack Compose (Modern Material3) |
                 |  - Encrypted SQLCipher (Offline DB)  |
                 |  - Sync Engine (HMAC / AES-256)      |
                 +-------------------+-------------------+
                                     |
                                REST API
                            (HTTPS Sync & HMAC)
                                     |
                 +-------------------+-------------------+
                 |        Laravel 11 Cloud Backend        |
                 |  - Sanctum API / Custom API Keys     |
                 |  - Double-Entry Ledger Validation    |
                 |  - PostgreSQL / SQLite Master DB     |
                 +---------------------------------------+
```

---

## Core Features

1. **Multi-Currency Hundi / Hawala Transaction Engine**
   - Direct support for AED, BDT, USD, SAR, MYR, INR, OMR, QAR, KWD.
   - Dual-currency rate calculation (Send Currency vs Pay Currency).
   - Instant dynamic profit margin analysis.

2. **Customer & Supplier Double-Entry Ledger**
   - Double-entry accounting for all transactions (Debit/Credit balance consistency).
   - Real-time ledger statements exportable to PDF and Excel.
   - Customer & Supplier profile management with dynamic contact linking.

3. **Multi-Wallet & Cashbook Management**
   - Multi-currency digital wallets (Cash, Bank, Agent, Bkash, Nagad).
   - Dynamic income and expense categorization.
   - Live net-worth and running liquid cash balance metrics.

4. **Biometric Security & Encryption**
   - End-to-end local data encryption powered by SQLCipher.
   - Hardware-backed biometric authentication (Fingerprint & Face Unlock).
   - Automatic session timeout and security masking.

5. **Bi-Directional Sync & Backup Engine**
   - Offline-first architecture allowing full operation without network.
   - Conflict-resolution delta sync mechanism (timestamp-based payload hashing).
   - Secure encrypted cloud backup and restore.

---

## Directory Structure

```
safa/
├── app/                        # Android Client (Kotlin & Jetpack Compose)
│   ├── src/main/java/com/safa/account/
│   │   ├── data/               # Models, DAOs, Database, API, Repositories
│   │   └── ui/                 # Screens, ViewModels, Theme, Components
├── backend/                    # Laravel 11 Backend API
│   ├── app/                    # Controllers, Models, Services, Middleware
│   ├── database/               # Migrations, Factories, Seeders
│   └── routes/                 # API Routes (`api.php`)
└── .github/workflows/          # CI/CD Automation (Build, Test, APK Release)
```

---

## Local Setup & Development Guide

### Prerequisites
- **Android Studio** (Ladybug / 2024.2+ recommended)
- **JDK 17**
- **PHP 8.3+** & **Composer 2.x** (for Backend)

---

### Running Android Mobile Client
1. Open Android Studio and choose **Open** -> Select `safa/` root or `safa/app`.
2. Create an `app/.env` file with your configuration:
   ```env
   SAFA_API_KEY=your_api_key_here
   SAFA_API_SECRET=your_api_secret_here
   SAFA_BASE_URL=http://10.0.2.2:8000/api/
   ```
3. Sync Gradle and build/run on Emulator or physical device:
   ```powershell
   ./gradlew assembleDebug
   ```

---

### Running Laravel Backend API
1. Navigate to the `backend` directory:
   ```powershell
   cd backend
   ```
2. Install PHP dependencies:
   ```powershell
   composer install
   ```
3. Setup environment configuration:
   ```powershell
   cp .env.example .env
   php artisan key:generate
   ```
4. Run migrations and database seeders:
   ```powershell
   php artisan migrate --seed
   ```
5. Start local server:
   ```powershell
   php artisan serve
   ```

---

## Automated Verification & Testing

### Running Tests
- **Android Unit & UI Tests**:
  ```powershell
  ./gradlew test
  ```
- **Laravel Backend Tests**:
  ```powershell
  cd backend
  php artisan test
  ```

---

## License & Security
Private & Proprietary Financial Software. All rights reserved.
