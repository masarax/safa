# Android performance baseline

SAFA uses Jetpack Macrobenchmark APIs in the dedicated `:benchmark` test module. Normal pull-request CI compiles the benchmark APK, validates deterministic performance contracts, and verifies the checked-in release profile. Wall-clock timing from shared hosted emulators is intentionally not a merge gate.

## Deterministic fixture

The benchmark-only app variant exposes `BenchmarkFixtureProvider`. Macrobenchmark runs in a separate process, so the test APK requests deterministic data through that provider and the target app writes the fixture through its own encrypted `LocalFirstStore`. The provider exists only in the benchmark source set and is absent from debug and production release APKs; it performs no production or test-server requests.

Business navigation uses the benchmark-only `BenchmarkHostActivity`. It renders the production dashboard, customer, supplier, transaction and wallet screens with a synthetic local operator through a `SafaViewModel` that has no `TokenManager`. This keeps permissions and real screen code in the measured path without storing credentials or calling production authentication/sync services. Cold/warm startup measurements still launch the real `MainActivity`, so the signed-out/login shell remains part of production startup coverage.

## Baseline Profile

The repository intentionally does not apply the `androidx.baselineprofile` Gradle plugin on the current AGP 9.3.1 line. Profile generation uses `BaselineProfileRule` directly from the Macrobenchmark instrumentation module.

On an API 28+ controlled device, generate a candidate profile with:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.safa.account.benchmark.SafaBaselineProfileGenerator
```

Review the generated instrumentation additional-output profile, then replace `app/src/main/baseline-prof.txt` with the accepted rules in the same pull request. The checked-in file is the production release profile contract and must continue to cover startup plus dashboard, customer, supplier, transaction, and wallet journeys. Required benchmark destinations fail the run when they cannot be reached; they are never silently skipped.

## Macrobenchmark run

Use a physical reference device (or dedicated hardware-stable lab device) with battery above 50%, normal thermal state, and no unrelated foreground/background workload. Run only the timing suite with:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.safa.account.benchmark.SafaMacrobenchmark
```

Retain raw benchmark JSON and trace artifacts with the release candidate for comparison. Hosted emulator CI may compile the benchmark APK and run functional instrumentation, but emulator timing is not accepted performance evidence.

## Budgets

`benchmark/performance-budgets.json` is the machine-readable source of controlled-device limits. Normalize the accepted Macrobenchmark measurements into a result file with this shape:

```json
{
  "cold_ms_p50": 0.0,
  "cold_ms_p95": 0.0,
  "warm_ms_p50": 0.0,
  "frame_ms_p95": 0.0,
  "jank_percent": 0.0
}
```

Validate `normalized-results.json` against the versioned budgets with:

```bash
python3 scripts/check-performance-budget.py normalized-results.json
```

To enforce the maximum allowed regression against the previously accepted reference-device result, include that result too:

```bash
python3 scripts/check-performance-budget.py normalized-results.json \
  --baseline accepted-baseline.json
```

Reference-device measurements must meet the versioned startup/frame budgets before release. Changes to the accepted device, fixture, iterations, warmups, or thresholds require reviewed evidence in the same change so regressions cannot be hidden by environment drift.

## CI policy

Pull requests compile `:benchmark`, run the budget/profile contract tests, and run normal app unit, lint, instrumentation, unsigned-release, and signed/minified-release gates. Functional hosted-emulator CI explicitly runs `:app:connectedDebugAndroidTest`; Macrobenchmark timing remains isolated from the correctness gate.

Release/nightly validation on the controlled device runs `SafaMacrobenchmark`, retains reports/traces, and compares measurements against the versioned budgets. Any regression blocks the release candidate until fixed or explicitly re-baselined through review.
