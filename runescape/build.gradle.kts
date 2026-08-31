plugins {
    application
}

dependencies {
    implementation(files("lib/stub.jar"))
    implementation(libs.openrs2.deob.annotations)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

application {
    mainClass = "client"
    applicationDefaultJvmArgs = listOf("-Xmx1024m", "-Dsun.java2d.noddraw=true")
}

tasks.run.configure {
    args = listOf("1", "1000", "local", "live", "english", "game0")
}

val r13Regression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks graphics preferences and renderer fallback without a game server or display."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("R13RegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

val audioSettingsRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks audio stream reset and one-shot queue handling without an audio device."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("AudioSettingsRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

val cameraZoomRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks mouse-wheel viewport routing and safe camera zoom bounds."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("CameraZoomControllerRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

val oldStyleHitsplatRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks old-style hitsplat display scaling without changing raw combat values"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("OldStyleHitsplatDisplayRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}

tasks.check { dependsOn(r13Regression, audioSettingsRegression, cameraZoomRegression, oldStyleHitsplatRegression) }
