package ru.citeck.ecos.webapp.servicedesk.domain.sla.converter

import kotlin.math.absoluteValue
import kotlin.time.Duration

object SlaDurationHumanReadableConverter {

    fun convert(duration: Duration): String {
        val humanReadable = duration.toComponents { hours, minutes, _, _ ->
            if (hours == 0L && minutes == 0) {
                return "0M"

            }

            var result = ""

            if (duration.isNegative()) {
                result += "-"
            }

            val hoursAbsolute = hours.absoluteValue
            if (hoursAbsolute > 0) {
                result += "${hoursAbsolute}H"
            }

            val minutesAbsolute = minutes.absoluteValue
            if (minutesAbsolute > 0) {
                result += if (result == "" || result == "-") {
                    "${minutesAbsolute}M"
                } else {
                    " ${minutesAbsolute}M"
                }
            }

            result
        }

        return humanReadable
    }
}

fun Duration.toSlaHmr(): String = SlaDurationHumanReadableConverter.convert(this)
