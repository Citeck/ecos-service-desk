package ru.citeck.ecos.webapp.servicedesk.domain.sla

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration


@Component
class SdDueDateService {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    //TODO: add support for business days, working hours, holidays, etc.
    fun getDueDate(duration: Duration): Instant {
        val from = Instant.now()

        val result = from.plus(duration.toJavaDuration())

        log.debug { "Due date for from=$from, duration=$duration, result=$result" }

        return result
    }


}
