plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
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

    compileOnly("io.github.nacvark:hudengine-api:1.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("--add-modules=jdk.httpserver")
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
