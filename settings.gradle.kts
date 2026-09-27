enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        if (providers.gradleProperty("localKmpSettings").isPresent) {
            mavenLocal()
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "mages"
include(":shared")
include(":androidApp")
include(":desktopApp")
include(":webApp")

// Local development against the kmp-settings checkout.
//
// A composite build is not an option here: dependencySubstitution has to satisfy every target
// Mages declares, and settings-ui-compose ships no linuxX64 variant, so commonMain fails to
// resolve. Publishing to the local Maven repository sidesteps that, because a module resolved
// from a repository may simply lack a target.
//
//   ./gradlew -p ../kmp-settings publishToMavenLocal
//   ./gradlew -PlocalKmpSettings <task>
//
// Off unless the flag is passed, so a release build always resolves Maven Central.
