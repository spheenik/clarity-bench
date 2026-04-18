plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":harness"))
    implementation("com.skadistats:clarity:3.1.3")
}

application {
    mainClass.set("spheenik.claritybench.BenchMain")
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
}
