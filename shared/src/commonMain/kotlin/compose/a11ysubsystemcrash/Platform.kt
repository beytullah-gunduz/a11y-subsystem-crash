package compose.a11ysubsystemcrash

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform