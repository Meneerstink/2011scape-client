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

val deadmanHudRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "A30: the Deadman HUD sections of rspsHudRegression on their own (per-mode spot, never over the fixed chatbox)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsHudRegressionTest")
    args("deadmanHudPerMode", "deadmanHudClearOfChat")
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

val stretchedScalingRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "A2: Resizable scaling stops at the last step that changes the game for the window (every window x every step, the real sidebar spinner)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("StretchedScalingRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

val rspsCameraRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the RuneLite Camera plugin (mouse remapping, speed, pitch limit, shake) and OSRS's mouse camera and zoom without a display."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsCameraRegressionTest")
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

val spellFiltersRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "N72: the OSRS spell filters - which spells hide (lacks level / runes / requirements, open menu), the layout hook, the probe."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("RspsSpellFiltersRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(spellFiltersRegression) }

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
    dependsOn(r13Regression, audioSettingsRegression, audioVolumeRegression, cameraZoomRegression, debugOverlayRegression)
}

// Night run 2026-10-12: Base37 decoded every real name as null (friends chat channel name -> "Not in chat").
val base37Regression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Base37 name encoding round-trips like the 2011 client (friends chat channel names, NameTools.normalise)"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("Base37RegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
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

// T2 (owner 2026-10-13): the client saves its own next frame as bugs\<report>.png after every `bug` command.
val bugShotRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the bug-report screenshot: server stems only, frame composed per mode (fixed/resizable/stretched), folder, wiring."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("BugShotRegressionTest")
    jvmArgs("-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
}

tasks.check { dependsOn(bugShotRegression) }
