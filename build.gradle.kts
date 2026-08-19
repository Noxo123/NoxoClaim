plugins { java }
group = "fr.noxodev"
version = "1.0.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT"); testImplementation("org.junit.jupiter:junit-jupiter:5.12.2") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("NoxoClaim-${project.version}.jar") }
