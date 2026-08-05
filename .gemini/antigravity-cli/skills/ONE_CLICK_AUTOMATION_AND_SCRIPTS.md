# One-Click Automation & CI/CD Scripts
**Project:** `com.safa.account`

## 1. Local Development Scripts (PowerShell / Bash)
Fast setup for local development.

### `setup.ps1`
```powershell
Write-Host "Setting up com.safa.account ecosystem..."
# Install backend dependencies
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate:fresh --seed
php artisan serve --port=8000 &

# Start Android Gradle Daemon
cd ../android
./gradlew clean build --daemon
Write-Host "Setup complete!"
```

## 2. GitHub Actions CI/CD Pipeline
Continuous integration to enforce code quality, security scanning, and automated builds.

**`.github/workflows/android-ci.yml`**
```yaml
name: Android CI/CD

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    
    - name: Grant execute permission for gradlew
      run: chmod +x android/gradlew
      
    - name: Run Unit Tests
      run: cd android && ./gradlew testDebugUnitTest
      
    - name: Build Debug APK
      run: cd android && ./gradlew assembleDebug
      
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: android/app/build/outputs/apk/debug/app-debug.apk
```

**`.github/workflows/laravel-ci.yml`**
```yaml
name: Laravel CI

on:
  push:
    branches: [ "main" ]

jobs:
  tests:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Setup PHP
      uses: shivammathur/setup-php@v2
      with:
        php-version: '8.2'
    - name: Install Dependencies
      run: cd backend && composer install -q --no-ansi --no-interaction --no-scripts --no-progress --prefer-dist
    - name: Execute Tests
      run: cd backend && php artisan test
```

## 3. Gradle Task Shortcuts
Add this to `android/build.gradle` for faster workflows.

```groovy
task cleanAndTest(type: Exec) {
    commandLine './gradlew', 'clean', 'testDebugUnitTest'
}

task generateReleaseWithKeystore(type: Exec) {
    commandLine './gradlew', 'assembleRelease', '-Pandroid.injected.signing.store.file=keystore.jks', '-Pandroid.injected.signing.store.password=secret'
}
```
