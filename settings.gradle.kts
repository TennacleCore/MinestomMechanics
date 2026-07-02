rootProject.name = "MinestomMechanics"

// Build/run against a LOCAL Minestom checkout instead of the published artifact, to verify Minestom changes before
// publishing them. Enable with -PlocalMinestom=../Minestom (or set localMinestom=../Minestom in gradle.properties).
// Gradle's composite build auto-substitutes net.minestom:minestom + net.minestom:testing with the checkout's projects
// (group net.minestom), ignoring the requested version. Off = the normal published dependency.
providers.gradleProperty("localMinestom").orNull?.let { includeBuild(it) }
