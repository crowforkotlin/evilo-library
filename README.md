# evilo-library

The Kotlin Multiplatform SDK for evilo plugins: portable media models, the
synchronous plugin/host protocol, and the Wasmline service interfaces a plugin
implements.

Published to Maven Central as `io.github.crowforkotlin.evilo:evilo-library`.

```kotlin
dependencies {
    api("io.github.crowforkotlin.evilo:evilo-library:2.0.0")
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
| `crow.evilo.plugin.api` | `EviloPluginService`, `EviloHostService`, `MangaSourceService`, descriptors, storage and HTTP contracts, and protocol compatibility rules |

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

To develop a plugin against uncommitted SDK changes, publish it locally and
build the plugin repository with its own local-SDK switch:

```sh
./gradlew publishToMavenLocal
```

## Releasing

```sh
git switch main
./.github/scripts/bump_version.py z
git add -A && git commit -m "chore(release): 2.0.0"
git tag library-v2.0.0
git push origin main library-v2.0.0
```

`.github/workflows/publish-library.yml` checks that the tag matches the version
and that the group in `build.gradle.kts` matches `EVILO_GROUP`, runs `:jvmTest`,
stages the publication into `~/.m2`, verifies every artifact has POM metadata
plus sources and javadoc JARs, gates the footprint (the free Central tier meters
monthly bytes and file counts), and only then uploads with
`publishToMavenCentral`.

Repository secrets, set once. Run these one at a time: `gh secret set` reads a
single value from the terminal, so a `for` loop makes every iteration fight the
same prompt.

The account token is mandatory:

```sh
gh secret set MAVEN_CENTRAL_USERNAME --repo crowforkotlin/evilo-library
```

```sh
gh secret set MAVEN_CENTRAL_PASSWORD --repo crowforkotlin/evilo-library
```

The signing key is mandatory:

```sh
gpg --armor --export-secret-keys YOUR_FINGERPRINT \
  | gh secret set MAVEN_CENTRAL_SIGNING_KEY --repo crowforkotlin/evilo-library
```

Its passphrase is required only when that key is protected:

```sh
gh secret set MAVEN_CENTRAL_SIGNING_KEY_PASSWORD --repo crowforkotlin/evilo-library
```

Both halves of the Central user token come from Portal -> Account -> Generate
User Token.

Find the fingerprint for `YOUR_FINGERPRINT` with:

```sh
gpg --list-secret-keys --keyid-format LONG
```

Piping the export straight into `gh` keeps the armored key out of your paste
buffer. `gpg` may ask for the key passphrase while exporting. An inherited `GNUPGHOME`
that points at a missing directory makes `gpg` fail before it reaches your
keyring; unset it or pass `GNUPGHOME="$HOME/.gnupg"` explicitly.

Whether `MAVEN_CENTRAL_SIGNING_KEY_PASSWORD` is required depends on the key. A
key with an `S2K` protection packet needs it; an unprotected key must not have it
set. The publish job inspects the armored key and fails with a named message
either way, so a wrong pairing never reaches the upload step.

Confirm they are present:

```sh
gh secret list --repo crowforkotlin/evilo-library
```

## License

MIT. See [LICENSE](LICENSE).
