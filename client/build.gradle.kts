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
    from(sourceSets.main.get().output)

    // Include runtime dependencies while excluding META-INF
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    manifest {
        attributes["Main-Class"] = "RS2Loader" // Replace with your actual main class
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
