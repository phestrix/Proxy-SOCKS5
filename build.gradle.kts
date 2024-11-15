plugins {
    kotlin("jvm") version "2.0.20"
    application
}

group = "ru.phestrix"
version = "1.0-SNAPSHOT"
val ktor_version: String by project
val kotlin_version: String by project
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-network:$ktor_version")
    implementation("io.ktor:ktor-utils:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation(kotlin("test"))
}

application{
    mainClass.set("ru.phestrix.src.main.kotlin.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar{
    manifest{
        attributes["Main-Class"] = "ru.phestrix.src.main.kotlin.MainKt"
    }
}

kotlin {
    jvmToolchain(17)
}