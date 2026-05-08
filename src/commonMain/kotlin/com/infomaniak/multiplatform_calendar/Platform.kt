package com.infomaniak.multiplatform_calendar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform