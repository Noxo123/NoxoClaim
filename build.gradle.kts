plugins {
    java
    id("com.gradleup.shadow") version "8.3.9"
}

group = "fr.noxodev"
version = "2.0.1"
description = "NoxoClaim - claims professionnels pour Paper 26.2 et 26.1"

repositories {
    mavenCentral()
    maven { name = "papermc"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { name = "jitpack"; url = uri("https://jitpack.io") }
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")
val paperApi = "io.papermc.paper:paper-api:${paperApiVersion.get()}"
val gitCommit = providers.environmentVariable("GITHUB_SHA")
    .orElse(providers.gradleProperty("noxoclaimCommit"))
    .orElse("unknown")

dependencies {
    compileOnly(paperApi)
    testImplementation(paperApi)

    implementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // HUDEngine is installed as a Paper plugin by NoxoClaim when enabled.
    // The API stays compile-only so we never duplicate HUDEngine classes in NoxoClaim.jar.
    compileOnly("io.github.nacvark:hudengine-api:1.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("build-info.properties") {
        expand("gitCommit" to gitCommit.get())
    }
}

tasks.test { useJUnitPlatform() }

tasks.jar { enabled = false }
tasks.shadowJar {
    archiveFileName.set("NoxoClaim-${project.version}.jar")
    mergeServiceFiles()
}

tasks.build { dependsOn(tasks.shadowJar) }
