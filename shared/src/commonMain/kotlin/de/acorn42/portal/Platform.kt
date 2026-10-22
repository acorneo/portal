package de.acorn42.portal

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform