plugins {
    application
}

dependencies {
    implementation(project(":runescape"))
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")
    implementation("com.beust:jcommander:1.82")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("RS2Loader") // Replace with your actual main class
    applicationDefaultJvmArgs = listOf("-Xmx1024m", "-Dsun.java2d.noddraw=true")
}

// K-01: dev mode (debug console, client QA commands) only with -Prsps.dev=true, which the owner's Start-RSPS.ps1 passes;
// the same switch as :runescape:run, so both launchers start the owner's client the same way.
tasks.named<JavaExec>("run") {
    systemProperty("rsps.dev", providers.gradleProperty("rsps.dev").getOrElse("false"))
    // Owner 2026-10-02: a test client for the agent's test server (tools\live\Start-TestServer.ps1) passes -Prsps.gamePort.
    providers.gradleProperty("rsps.gamePort").orNull?.let { systemProperty("rsps.gamePort", it) }
    // Owner A2 2026-10-11: agent test clients pass -Prsps.preferencesDir so they never write the owner's
    // jagex_runescape_preferences.dat or ~/.rsps78 (window size, HUD position, sidebar pins). The owner's launcher passes nothing.
    providers.gradleProperty("rsps.preferencesDir").orNull?.let { systemProperty("rsps.preferencesDir", it) }
    // Agent team 2026-10-14: the agent's test client takes clicks/keys/screenshots on this local port (com.jagex.TestHook)
    // instead of the owner's real mouse and screen. Only tools\live\RestartClient.ps1 passes it.
    providers.gradleProperty("rsps.testHook").orNull?.let { systemProperty("rsps.testHook", it) }
    providers.gradleProperty("rsps.testSound").orNull?.let { systemProperty("rsps.testSound", it) }

    // Owner D3 2026-10-11 (crash "NoClassDefFoundError: Static304", 2026-10-10 21:20:02): the client ran straight from
    // runescape/build/libs/runescape.jar; 75 s after the owner started his client another session ran `gradlew :client:jar`,
    // which rewrote that jar, and the running client could not load its next class. Every launch now runs from a copy of
    // this repository's own build output in build/run-snapshots/<build id> - the game server does the same with
    // build/live-lib. A rebuild gives a new id and a new folder; a running client keeps reading its own copy. Jars from the
    // Gradle cache never change and are used where they are.
    val snapshotRoot = layout.buildDirectory.dir("run-snapshots").get().asFile
    val repoRoot = rootDir.absolutePath
    val snapshotClasspath = objects.fileCollection()
    doFirst {
        val exec = this as JavaExec
        val entries = exec.classpath.files.filter { it.exists() }
        val own = entries.filter { it.absolutePath.startsWith(repoRoot) }
        fun stamp(file: File): Long = file.walkTopDown().filter { it.isFile }
            .fold(0L) { acc, f -> acc * 31 + f.length() * 7 + f.lastModified() + f.path.hashCode() }
        val id = own.fold(17L) { acc, f -> acc * 31 + stamp(f) }.toULong().toString(16)
        val dir = File(snapshotRoot, id)
        val name = { index: Int, file: File -> "$index-${file.name}" }
        if (!File(dir, ".complete").exists()) {
            val temp = File(snapshotRoot, "$id.tmp-${ProcessHandle.current().pid()}")
            temp.deleteRecursively()
            own.forEachIndexed { index, file -> file.copyRecursively(File(temp, name(index, file)), overwrite = true) }
            File(temp, ".complete").writeText(own.joinToString(System.lineSeparator()) { it.path })
            // Two launches of the same build at once: the first rename wins, the other copy is dropped.
            if (!temp.renameTo(dir)) temp.deleteRecursively()
        }
        dir.setLastModified(System.currentTimeMillis())
        // Snapshots not launched for 3 days: a client that old has loaded its classes long ago.
        val cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        snapshotRoot.listFiles()?.filter { it != dir && it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        exec.classpath = snapshotClasspath.from(entries.map { file ->
            val index = own.indexOf(file)
            if (index < 0) file else File(dir, name(index, file))
        })
        println("[78] client runs from ${dir.path}")
    }
}

// Configure the JAR task to include dependencies
tasks.named<Jar>("jar") {
    // The BouncyCastle packages the client's RSA key reading needs (a local value: the configuration cache cannot hold script fields).
    val bouncyCastleKeep = listOf(
        Regex("org/bouncycastle/LICENSE\\.class"),
        Regex("org/bouncycastle/asn1/[^/]+"),
        Regex("org/bouncycastle/asn1/(pkcs|x509|x9)/[^/]+"),
        Regex("org/bouncycastle/crypto/[^/]+"),
        Regex("org/bouncycastle/crypto/params/[^/]+"),
        Regex("org/bouncycastle/math/.+"),
        Regex("org/bouncycastle/util/.+"),
    )

    from(sourceSets.main.get().output)

    // Include runtime dependencies while excluding META-INF
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
        // Owner 2026-10-17 (T18): the agent team's test hook (local click/key/screenshot port) is never in the player jar;
        // the client reaches it only through RspsTestBridge, which stays off when these classes are missing.
        exclude("com/jagex/TestHook.class", "com/jagex/TestHook$*.class", "RspsTestDetail.class", "RspsTestDetail$*.class")
        // Owner 2026-10-11: the 27 MB player client could not be sent over Discord. Left out of the player jar:
        // - META-INF/versions: Java 9+ copies of library classes, never read (this manifest is not Multi-Release);
        // - BouncyCastle beyond what com.jagex.crypto reaches (jdeps closure 2026-10-11: 264 classes in asn1, asn1.pkcs/x509/x9,
        //   crypto, crypto.params, math, util; no reflection there). The rest (post-quantum, CMS, TLS ...) was ~8 MB.
        exclude("META-INF/versions/**")
        exclude { element ->
            val path = element.path
            !element.isDirectory && path.startsWith("org/bouncycastle/") && bouncyCastleKeep.none { it.matches(path) }
        }
    }
    includeEmptyDirs = false

    manifest {
        attributes["Main-Class"] = "RS2Loader" // Replace with your actual main class
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
