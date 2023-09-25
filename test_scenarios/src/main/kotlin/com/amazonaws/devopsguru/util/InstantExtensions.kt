package com.amazonaws.devopsguru.util

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

operator fun Instant.minus(other: Instant): Duration =
    java.time.Duration.between(other, this).toKotlinDuration()

operator fun Instant.plus(duration: Duration): Instant = this.plus(duration.toJavaDuration())
