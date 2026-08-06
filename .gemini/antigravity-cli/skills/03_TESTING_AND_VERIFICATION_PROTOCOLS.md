# SAFA System Skill 03: Testing, Validation & Verification Protocols

## 1. Mandatory Pre-Commit Verification Checklist
Before declaring any task finished or fixed, an AI agent MUST complete the following verification steps:

```
+-----------------------------------------------------------------------+
|                       Pre-Commit Checklist                            |
+-----------------------------------------------------------------------+
| [ ] 1. Compile Check: Android project builds cleanly with Gradle.       |
| [ ] 2. Backend Check: Laravel tests run without failures.             |
| [ ] 3. Zero Lint / Syntax Errors: No breaking imports or type mismatches.
| [ ] 4. Security Check: No raw API secrets hardcoded in git.            |
+-----------------------------------------------------------------------+
```

---

## 2. Testing Execution Commands

### Android Client Automated Verification
Execute from the project root directory (`safa/`):

```powershell
# Compile Android Debug APK
./gradlew assembleDebug --stacktrace

# Run Android Unit & ViewModel Tests
./gradlew test
```

### Laravel Backend Automated Verification
Execute from the `backend` directory (`safa/backend/`):

```powershell
# Run Laravel PHPUnit Test Suite
php artisan test

# Verify Database Migration Status
php artisan migrate:status
```

---

## 3. Debugging Protocol for AI Agents
If a test or build command fails:
1. **Never guess the fix**: Read the exact error log output first.
2. **Never suppress errors**: Do not swallow exceptions with empty try-catch blocks.
3. **Trace the source**: Fix the underlying root cause in either Android Kotlin DAOs/ViewModels or Laravel Controllers/Migrations.
4. **Re-run verification**: Verify that the test suite passes cleanly after making code edits.
