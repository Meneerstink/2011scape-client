pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://repo.openrs2.org/repository/openrs2-snapshots")
        // RuneLite's FlatLaf build (net.runelite:flatlaf): the 78 Plugins sidebar uses RuneLite's own look and feel.
        maven(url = "https://repo.runelite.net")
    }
}

rootProject.name = "2011scape-client"

include(
    "client",
    "loader",
    "runescape",
)
