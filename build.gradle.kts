plugins {
    java
}

group = "fr.noxodev"
version = "1.1.4"
description = "NoxoClaim - claims par chunks pour Paper 26.2 et 26.1"

repositories {
    mavenCentral()
    maven { name = "papermc"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { name = "jitpack"; url = uri("https://jitpack.io") }
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")
val paperApi = "io.papermc.paper:paper-api:${paperApiVersion.get()}"

dependencies {
    compileOnly(paperApi)
    testImplementation(paperApi)

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

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

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("NoxoClaim-${project.version}.jar")
}
