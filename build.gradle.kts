import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.URI

plugins {
    id("org.springframework.boot") version "2.6.6"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.spring") version "1.6.10"
    id("com.gorylenko.gradle-git-properties") version "2.2.2"
    kotlin("plugin.serialization") version "1.6.21"
//    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"

    java
}

configurations {
    all {
        exclude(module = "spring-boot-starter-tomcat")
    }
}

group = "com.steelhouse"
version = "1.0.1"

repositories {
    mavenCentral()
    maven {
        val releasesUrl = "https://steelhouse.jfrog.io/steelhouse/releases"
        val snapshotsUrl = "https://steelhouse.jfrog.io/steelhouse/snapshots"
        url = if (version.toString().endsWith("SNAPSHOT")) URI(snapshotsUrl) else URI(releasesUrl)
        credentials {
            username = project.property("artifactory_user") as String
            password = project.property("artifactory_password") as String
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:2.3.0")
    }
}

dependencies {
    implementation("com.github.avro-kotlin.avro4k:avro4k-core:1.6.0")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("javax.inject:javax.inject:1")
    implementation("org.springframework.kafka:spring-kafka:2.9.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(module = "spring-boot-starter-tomcat")
    }

    implementation("com.github.ben-manes.caffeine:caffeine:2.7.0")
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.apache.httpcomponents:httpclient:4.5.8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:1.15")
    implementation("io.springfox:springfox-swagger2:2.8.0")
    implementation("io.springfox:springfox-swagger-ui:2.8.0")

    implementation("io.grpc:grpc-netty-shaded:1.46.0")
    implementation("io.grpc:grpc-protobuf:1.46.0")
    implementation("io.grpc:grpc-stub:1.46.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.amazonaws:aws-java-sdk-s3:1.12.175")

    implementation("com.beeswax:beeswax-api:2021-02-05")
    implementation("io.lettuce:lettuce-core:6.1.8.RELEASE")

    implementation("com.google.code.gson:gson:2.9.0")

    implementation("io.micrometer:micrometer-core:1.8.5")
    implementation("io.micrometer:micrometer-registry-prometheus:1.8.5")

    implementation("javax.validation:validation-api:2.0.1.Final")
    // https://mvnrepository.com/artifact/org.slf4j/api
    implementation("org.slf4j:slf4j-api:1.7.36")

    // implementation("commons-validator:commons-validator:1.7.0")
    // implementation("javax.validation:name:validation-api:2.0.1.Final")
    // implementation("io.awspring.cloud:spring-cloud-starter-aws-messaging")

    // implementation(platform("software.amazon.awssdk:bom:2.15.69"))
    // implementation("software.amazon.awssdk:sqs")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.9.3.kotlin12")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    implementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

springBoot {
    buildInfo {
        properties {
            name = "membership-consumer"
        }
    }
    mainClass.set("com.steelhouse.membership.Application")
}

gitProperties {
    dateFormat = "EEEE, MMMM dd, YYYY 'at' h:mm:ss a z"
    dateFormatTimeZone = "GMT-07:00"
    keys = listOf(
        "git.branch",
        "git.build.version",
        "git.commit.id",
        "git.commit.id.abbrev",
        "git.commit.message.full",
        "git.commit.time"
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks {
    register<Zip>("fatZip") {
        from("src/main/resources", "build/libs")
        include("*")
        exclude("application-local.yml", "application-pass.yml")
        into("/")
        archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")
    }

    "assemble" {
        dependsOn("fatZip")
    }

    getByName<BootJar>("bootJar") {
        launchScript()
        archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")
    }

    wrapper {
        gradleVersion = "7.4.2"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
