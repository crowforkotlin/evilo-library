# evilo-library

The Kotlin Multiplatform SDK for evilo plugins.

```kotlin
dependencies {
    api("io.github.crowforkotlin.evilo:evilo-library:2.0.1")
}
```

## Commands

```sh
./gradlew :jvmTest
./gradlew publishToMavenLocal
```

## Release

- The version in `gradle.properties` is a protocol contract: `z` routine, `y` deliberate, `x` breaking. Raise it with `./.github/scripts/bump_version.py z` (or `y`/`x`).
- Push tag `library-v<version>`; CI verifies and publishes to Maven Central.
- Secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_CENTRAL_SIGNING_KEY`, plus `MAVEN_CENTRAL_SIGNING_KEY_PASSWORD` for a protected key.
