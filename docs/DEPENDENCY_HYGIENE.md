# SAFA Dependency Hygiene

SAFA production dependencies must map to an implemented product capability. The Android production stack is Kotlin/Compose, Retrofit/OkHttp/Moshi, WorkManager, DataStore, AndroidX Security and the encrypted `LocalFirstStore`/SQLiteOpenHelper persistence layer. Laravel is the server backend. Firebase, Room and SQLCipher are not part of the current production architecture.

## Change review

For every new Android platform/backend dependency or Gradle plugin:

1. identify the implemented SAFA feature that requires it;
2. confirm an existing dependency cannot provide the same capability;
3. inspect the resolved dependency graph for unexpected transitive runtime artifacts;
4. verify release R8/minification behavior in Android Production CI;
5. update `docs/architecture.md` if the dependency changes a production technology boundary;
6. remove aliases, plugins, configuration files and shrinker rules when a dependency is removed.

Repository review should include source, manifest, resources, `app/build.gradle.kts`, `gradle/libs.versions.toml` and `.github/workflows`. A platform SDK must not be retained merely for a planned or unused feature.

## Release audit

Before a production release run:

```bash
./gradlew :app:dependencies
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Review the runtime dependency output against the architecture above. Any Firebase/Room/SQLCipher artifact requires an explicit implemented feature, architecture update and regression coverage before it can be accepted.

## GitHub Actions update process

Every external action under `.github/workflows` must use a reviewed 40-character commit SHA. Keep the exact release tag in an adjacent comment. To update an action:

1. review the upstream release notes and source diff from the currently pinned release;
2. resolve the intended release tag directly from the action's official Git repository;
3. pin the dereferenced commit SHA (use the peeled `^{}` commit for annotated tags);
4. update the exact version comment and run all affected workflows;
5. never replace a SHA with a floating major tag, branch or mutable reference.

Dependabot or another updater may propose SHA changes, but a maintainer must still review the upstream diff and successful CI before merging.
