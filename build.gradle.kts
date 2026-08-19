plugins {
    java
}

group = "fr.noxodev"
version = "1.1.0"
description = "NoxoClaim - claims par chunks pour Paper 26.2 et 26.1"

repositories {
    mavenCentral()
    maven { name = "papermc"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { name = "jitpack"; url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.1.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("NoxoClaim-${project.version}.jar") }
