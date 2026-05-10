rootProject.name = "clarity-bench"

include("harness")
include("v5.0.0")
include("v4.0.1")
include("v3.1.3")

project(":v5.0.0").projectDir = file("v5.0.0")
project(":v4.0.1").projectDir = file("v4.0.1")
project(":v3.1.3").projectDir = file("v3.1.3")
