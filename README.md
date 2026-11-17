# evilo-library

The Kotlin Multiplatform SDK for [evilo](https://github.com/crowforkotlin/evilo) plugins:
portable media models, the synchronous plugin/host protocol, and the Wasmline
service interfaces a plugin implements.

Published to Maven Central as `io.github.crowforkotlin.evilo:evilo-library`.

```kotlin
dependencies {
    api("io.github.crowforkotlin.evilo:evilo-library:1.0.0")
}
```

## Targets

`jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `js` (browser), `wasmJs`
(browser), and `wasmWasi` (Node). Plugins need `jvm` for host tests and
`wasmWasi` for the guest binary the host actually loads.

## Contents

| Package | Purpose |
| --- | --- |
| `crow.evilo.model` | Serializable media models: items, chapters, pages, streams |
| `crow.evilo.plugin.api` | `EviloPluginService`, `EviloHostService`, `MangaSourceService`, descriptors, permissions, and protocol compatibility rules |

The protocol is JSON envelopes over generated Wasmline bridge calls, so additive
fields stay independent of the bridge ABI. Unknown optional fields are tolerated;
`ProtocolRequirement.incompatibility` decides whether a host can load a plugin.

## Versioning

The version is a protocol contract, not a release counter. It moves only when
this repository changes, and plugin repositories pin it:

| Raise | Meaning |
| --- | --- |
| `z` | routine: new model fields, optional capabilities, fixes. Old plugins keep working. |
| `y` | a larger step plugins should adopt deliberately; still source-compatible. |
| `x` | breaking: plugin repositories must update to compile or load. |

Positions carry like an odometer with base 10 (`1.0.9 + z = 1.1.0`), not semver.
Use the script rather than editing by hand:

```sh
./.github/scripts/bump_version.py z
```

## Local development

```sh
./gradlew :jvmTest          # models and protocol rules
./gradlew publishToMavenLocal
```

`publishToMavenLocal` needs no GPG key; CI supplies signing inputs from
repository secrets. Consumers can then resolve the coordinate without a composite
build.

To develop a plugin against uncommitted SDK changes, point the plugin repository
at this directory instead of a published version:

```sh
./gradlew :evilo-plugin-core:jvmTest -Pevilo.sdkPath=../evilo-library
```

## Releasing

```sh
git switch main
./.github/scripts/bump_version.py z
git add -A && git commit -m "chore(release): 1.0.1"
git tag library-v1.0.1
git push origin main library-v1.0.1
```

`.github/workflows/publish-library.yml` checks that the tag matches the version
and that the group in `build.gradle.kts` matches `EVILO_GROUP`, runs `:jvmTest`,
stages the publication into `~/.m2`, verifies every artifact has POM metadata
plus sources and javadoc JARs, gates the footprint (the free Central tier meters
monthly bytes and file counts), and only then uploads with
`publishToMavenCentral`.

Required repository secrets, set once:

```sh
./.github/scripts/setup_central_secrets.sh --from-key 0B4189D9794C625F
```

Run it from a terminal, not through a pipe: it prompts once per value and does
not echo them. `--from-key` exports the signing key straight from your local
GnuPG keyring so the armored key never passes through a paste buffer.

Do not loop `gh secret set NAME` without `--body`: each call opens its own
interactive prompt on the same terminal, so a `for` loop delivers values to the
wrong secrets or hangs.

A version already on Central is skipped rather than republished, so re-running a
tag is harmless. Verify a release with:

```sh
curl -sI https://repo1.maven.org/maven2/io/github/crowforkotlin/evilo/evilo-library/1.0.1/evilo-library-1.0.1.pom | head -1
```

## License

MIT. See [LICENSE](LICENSE).
