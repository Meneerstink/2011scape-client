plugins {
    application
}

dependencies {
    implementation(files("lib/stub.jar"))
    implementation(libs.openrs2.deob.annotations)
    // 78 Plugins sidebar: RuneLite's look and feel (FlatLaf, RuneLite build) - rsps.runelite.RuneLiteLAF.
    implementation("net.runelite:flatlaf:3.2.5-rl4")
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
    // K-01/K-02: the debug console and client-side QA commands (zoom, ortho, ...) only exist with -Drsps.dev=true.
    // Players get LIVE by default; Launch-RSPS.ps1 passes -Prsps.dev=true for the owner's local workflow.
    systemProperty("rsps.dev", providers.gradleProperty("rsps.dev").getOrElse("false"))
}

val rspsHudRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the RSPS information overlays (payloads, values, settings, ground item labels) without a server or display."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsHudRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

val stretchedModeRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks RuneLite Stretched Mode layout, toolkit gating and mouse scaling without a display or GL context."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("StretchedModeRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
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

val audioVolumeRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the shared ambient volume gain used by location and area sounds."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "AudioVolumeRegressionTest"
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

val runePouchRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks that pouch runes count inside the spellbook rune counter (script 19) and the scripts it calls."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsRunePouchRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(runePouchRegression) }

val oldStyleHitsplatRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks old-style hitsplat, hitpoints and prayer display scaling"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("OldStyleHitsplatDisplayRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}

val debugOverlayRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks debug overlay startup defaults and explicit toggle behavior"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("DebugOverlayRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}

val deadmanWorldMapRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks Deadman safe/dangerous worldmap boundary projection."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("DeadmanWorldMapOverlayRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(deadmanWorldMapRegression) }

val combatTabNameFitRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the combat tab weapon name stays on one line (smaller cache font for long names)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("CombatTabNameFitRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(combatTabNameFitRegression) }

val geSetsLayoutRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the GE item sets grid (script 676) starts every set category in column 0 (bug 211448)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("GeSetsLayoutRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(geSetsLayoutRegression) }

val rspsBankRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the 78 client's OSRS bank scripts (RspsBank: item / side inventory menus, buttons) on a stack model (E7)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsBankRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(rspsBankRegression) }

tasks.check {
    dependsOn(r13Regression, audioSettingsRegression, audioVolumeRegression, cameraZoomRegression, oldStyleHitsplatRegression, debugOverlayRegression)
}

val graphicsDisplayRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("GraphicsDisplayRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}
val desktopDisplayRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("DesktopDisplayRegressionTest")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}
tasks.check { dependsOn(graphicsDisplayRegression) }

val summoningNpcAudioRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks provisional QC3 ready/walk/run/crawl coverage for all 78 Summoning familiars."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jagex.game.runetek6.config.npctype.SummoningNpcAudioRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(summoningNpcAudioRegression) }

val rspsRoofFloodRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the roof flood (Static409) around the Royal Hall balconies (0x20): no floating ring wall columns."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsRoofFloodRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(rspsRoofFloodRegression) }
