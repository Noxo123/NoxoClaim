plugins {
    java
}

group = "fr.noxodev"
version = "1.1.2"
description = "NoxoClaim - claims par chunks pour Paper 26.2 et 26.1"

repositories {
    mavenCentral()
    maven { name = "papermc"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { name = "jitpack"; url = uri("https://jitpack.io") }
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")

dependencies {
    // Paper 26.2 par défaut. Le CI peut compiler aussi contre Paper 26.1.2.
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")

    // VaultAPI 1.7 déclare une vieille dépendance Bukkit (1.13.1) qui entre
    // en conflit avec Paper 26.x. Bukkit/Paper est déjà fourni par Paper.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
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
