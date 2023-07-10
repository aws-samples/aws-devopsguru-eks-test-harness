import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    application
}

group = "com.amazonaws"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    val awsSdkVersion = "0.28.0-beta"
    val coroutinesVersion = "1.7.2"
    val kotlinVersion = "1.8.22"
    val log4j2Version = "2.20.0"

    implementation("aws.sdk.kotlin:ec2:$awsSdkVersion")
    implementation("aws.sdk.kotlin:eks:$awsSdkVersion")
    implementation("com.github.ajalt.clikt:clikt:4.0.0")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta14")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.2.0")
    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.slf4j:slf4j-api:2.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
}

application { mainClass.set("com.amazonaws.devopsguru.Main") }

configurations {
    compileClasspath {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
        vendor.set(JvmVendorSpec.AMAZON)
    }
}

tasks {
    test { useJUnitPlatform() }
    withType<KotlinCompile> { kotlinOptions { jvmTarget = JavaVersion.VERSION_17.majorVersion } }

    register<Exec>("buildAndRun") {
        dependsOn(build)
        dependsOn(installDist)
        commandLine("build/install/devopsguru-eks-scenarios/bin/devopsguru-eks-scenarios", "--help")
        standardOutput = System.out
    }

    installDist { destinationDir = projectDir.resolve("scripts") }

    build { finalizedBy(installDist) }
}
