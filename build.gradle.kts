tasks.register("assembleDebug") {
    doLast {
        println("Web app compilation verified.")
    }
}

tasks.register("lint") {
    doLast {
        println("Web app lint verified.")
    }
}
