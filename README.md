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

Required repository secrets, set once. Run these one at a time and paste the
value at the prompt; `gh secret set` reads a single value per command, so putting
it in a `for` loop makes every iteration fight the same terminal.

```sh
gh secret set MAVEN_CENTRAL_USERNAME --repo crowforkotlin/evilo-library
gh secret set MAVEN_CENTRAL_PASSWORD --repo crowforkotlin/evilo-library
gh secret set MAVEN_CENTRAL_SIGNING_KEY_PASSWORD --repo crowforkotlin/evilo-library
```

Both halves of the Central user token come from Portal -> Account -> Generate
User Token. For the signing key, export it from your keyring directly so the
armored key never passes through a paste buffer:

```sh
GNUPGHOME="$HOME/.gnupg" gpg --armor --export-secret-keys 0B4189D9794C625F \
  | gh secret set MAVEN_CENTRAL_SIGNING_KEY --repo crowforkotlin/evilo-library
```

`gnupg` may ask for the key passphrase while exporting. If you omit
`GNUPGHOME`, an inherited `GNUPGHOME` pointing at a missing directory makes `gpg`
fail before it reaches your keyring.

Confirm all four are present:

```sh
gh secret list --repo crowforkotlin/evilo-library
```

## License

MIT. See [LICENSE](LICENSE).
