plugins {
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation(project(":harness"))
    implementation("com.skadistats:clarity:5.0.0-SNAPSHOT") {
        isChanging = true
    }
}

application {
    mainClass.set("spheenik.claritybench.parsers.ClarityParse")
}
