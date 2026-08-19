plugins {
    java
}

group = "fr.noxodev"
version = "1.0.0"

description = "NoxoClaim - Système de claims pour Paper 26.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.1 — Java 25
    compileOnly("io.papermc.paper:paper-api:26.1.build.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
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
