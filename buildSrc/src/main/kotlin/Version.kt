sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) {
    abstract val name: String
    abstract val code: Long

    class Alpha(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-alpha.$build"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
    }

    class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-beta.$build"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
    }

    class Stable(versionMajor: Int, versionMinor: Int, versionPatch: Int) :
        Version(versionMajor, versionMinor, versionPatch) {
        override val name: String
            get() = "${major}.${minor}.${patch}"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
    }

    class ReleaseCandidate(
        versionMajor: Int,
        versionMinor: Int,
        versionPatch: Int,
        versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-rc.$build"

        override val code: Long
            get() =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
    }
}

// private const val ABI = 1L
private const val BUILD = 10L
private const val VARIANT = 100L
private const val PATCH = 10_000L
private const val MINOR = 1_000_000L
private const val MAJOR = 100_000_000L

private const val STABLE = VARIANT * 4
private const val ALPHA = VARIANT * 1
private const val BETA = VARIANT * 2
private const val RELEASE_CANDIDATE = VARIANT * 3

/** Version used for everyday (non-release) builds. Releases take their version from the git tag. */
val currentVersion: Version = Version.Stable(versionMajor = 0, versionMinor = 1, versionPatch = 6)

/**
 * Turns a release tag such as "v1.2.3", "v1.2.3-beta.1" or "v1.2.3-rc.2" into a [Version], so
 * pushing a tag is all that is needed to set the app's version name and version code.
 */
fun versionFromTag(tag: String): Version {
    val match =
        Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?$""").matchEntire(tag.trim())
            ?: error("Version tag '$tag' must look like v1.2.3 or v1.2.3-beta.1")
    val (major, minor, patch, type, build) = match.destructured
    val ma = major.toInt()
    val mi = minor.toInt()
    val pa = patch.toInt()
    require(ma <= 20 && mi < 100 && pa < 100) { "Use a major number up to 20 and minor/patch below 100" }
    val b = build.ifEmpty { "0" }.toInt()
    require(b < 10) { "Pre-release numbers must be below 10 (e.g. beta.9)" }
    return when (type) {
        "alpha" -> Version.Alpha(ma, mi, pa, b)
        "beta" -> Version.Beta(ma, mi, pa, b)
        "rc" -> Version.ReleaseCandidate(ma, mi, pa, b)
        else -> Version.Stable(ma, mi, pa)
    }
}
