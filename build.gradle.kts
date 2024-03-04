val kotlinxVersion: String by project
val kotlinxCoroutinesCoreName: String by project
val kotlinxCoroutinesRx3Name: String by project
val kotlinxCoroutinesTestName: String by project
val kotlinxSerialVersion: String by project
val kotlinxJsonSerializerName: String by project
val kotlinTestName: String by project
val kotlinTestVersion: String by project
val rxJavaName: String by project
val rxjavaVersion: String by project
val rxKotlinVersion: String by project
val rxKotlinName: String by project

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization").version("1.8.10")
}

group = "net.il"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("$kotlinxCoroutinesCoreName: $kotlinxVersion")
    implementation("$kotlinxCoroutinesRx3Name:$kotlinxVersion")
    implementation("$kotlinxJsonSerializerName:$kotlinxSerialVersion")
    implementation("$rxKotlinName:$rxKotlinVersion")
    implementation("$rxJavaName:$rxjavaVersion")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("$kotlinxCoroutinesTestName:$kotlinxVersion")
}

kotlin {
    jvmToolchain(17)
}

//tasks.register<Exec>("Ingen Filesystem Setup") {
//    val homePath = System.getProperty("user.home")
//    workingDir = File(homePath)
//    if (workingDir.exists()) {
//
//    } else workingDir.mkdirs()
//
//    this.
//    commandLine(listOf(""))
//}