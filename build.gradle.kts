import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.wasmline)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.crowforkotlin.evilo"
// The protocol contract plugin authors compile against. It moves only when this
// module changes: bump it with .github/scripts/bump_version.py before tagging.
version = providers.gradleProperty("EVILO_LIBRARY_VERSION_NAME").get()

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    android {
        // Preserved from the module's previous home so published AAR metadata and
        // R class names do not move with the repository.
        namespace = "crow.evilo.library"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.get())
        }
        withHostTest {}
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.get())
        }
    }
    iosArm64()
    iosSimulatorArm64()
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets.commonMain.dependencies {
        api(libs.wasmline)
        api(libs.kotlinx.serialization.core)
        api(libs.kotlinx.serialization.json)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlin.test)
    }
}

configure<MavenPublishBaseExtension> {
    configure(
        platform = KotlinMultiplatform(
            // Central accepts placeholder javadoc JARs; generating Dokka HTML per target
            // duplicated roughly 1 MiB across eight artifacts. The KDoc lives in this
            // repository, so placeholders keep the quota for real code.
            javadocJar = JavadocJar.Empty(),
            androidVariantsToPublish = emptyList(),
        ),
    )
    publishToMavenCentral(automaticRelease = true)
    // Local `publishToMavenLocal` must not require a GPG key; CI provides signing inputs.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.secretKeyRingFile").isPresent
    ) {
        signAllPublications()
    }
    pom {
        name.set("evilo library")
        description.set(
            "Kotlin Multiplatform media models and the plugin/host protocol used by the evilo " +
                "application and its Wasmline plugins.",
        )
        url.set("https://github.com/crowforkotlin/evilo-library/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("crowforkotlin")
                name.set("wuya")
            }
        }
        scm {
            url.set("https://github.com/crowforkotlin/evilo-library/")
            connection.set("scm:git:https://github.com/crowforkotlin/evilo-library.git")
            developerConnection.set("scm:git:ssh://git@github.com/crowforkotlin/evilo-library.git")
        }
    }
}
