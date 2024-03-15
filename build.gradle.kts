val spName: String by project
val spVersion: String by project
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
    id("maven-publish")
}

group = "com.github.ks288"
version = "1.0.0"

dependencies {
    api("$kotlinxCoroutinesCoreName: $kotlinxVersion")
    api("$kotlinxCoroutinesRx3Name:$kotlinxVersion")
    api("$kotlinxJsonSerializerName:$kotlinxSerialVersion")
    api("$rxKotlinName:$rxKotlinVersion")
    api("$rxJavaName:$rxjavaVersion")
    api("$spName:$spVersion")
    testImplementation("$kotlinxCoroutinesTestName:$kotlinxVersion")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
