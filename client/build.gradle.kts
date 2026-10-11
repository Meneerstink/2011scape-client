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
