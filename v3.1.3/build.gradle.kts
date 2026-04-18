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

tasks.register<JavaExec>("reproPropchangeCS2") {
    group = "verification"
    description = "Reproducer: clarity 3.1.3 + OnEntityPropertyChanged wildcard listener + CS2 demo. -Pdemo=<path> to swap."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("spheenik.claritybench.v313.repro.PropchangeCS2")
    val demo = (project.findProperty("demo") as? String)
            ?: "/home/spheenik/projects/replays/csgo/s2/issue-345/liquid-vs-betboom-m1-mirage.dem"
    args(demo)
}
