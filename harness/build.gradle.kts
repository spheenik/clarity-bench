plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    api("org.slf4j:slf4j-api:2.0.13")
    api("ch.qos.logback:logback-classic:1.5.32")
}
