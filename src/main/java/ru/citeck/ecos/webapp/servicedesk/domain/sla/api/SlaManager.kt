package ru.citeck.ecos.webapp.servicedesk.domain.sla.api

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SdDueDateService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaParametersProvider
import java.time.Instant
import kotlin.time.toJavaDuration

@Component
class SlaManager(
    private val slaParametersProvider: SlaParametersProvider,
    private val recordsService: RecordsService,
    private val dueDateService: SdDueDateService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun getDueDates(sdRequest: EntityRef): SlaDueDates {
        if (sdRequest.isEmpty()) {
            error("Empty SD request not allowed")
        }

        val sdAtts = recordsService.getAtts(sdRequest, SdRequestAtts::class.java)
        val priority = SdPriority.from(sdAtts.priority)

        val slaDurations = slaParametersProvider.get(sdAtts.client, priority)

        val timeFirstReaction = dueDateService.getDueDate(slaDurations.timeFirstReaction)
        val timeToResolve = dueDateService.getDueDate(slaDurations.timeResolve)

        val timeToAutoClose = dueDateService.getDueDate(slaDurations.timeToAutoClose)

        val dueDates = SlaDueDates(
            timeFirstReaction = timeFirstReaction,
            timeToResolve = timeToResolve,
            notificationToExecutorTimeReaction = timeFirstReaction.minus(
                slaDurations.notificationToExecutorTimeReaction.toJavaDuration()
            ),
            notificationToExecutorTimeResolve = timeToResolve.minus(
                slaDurations.notificationToExecutorTimeResolve.toJavaDuration()
            ),
            notificationToSupervisorTimeReaction = timeFirstReaction.minus(
                slaDurations.notificationToSupervisorTime.toJavaDuration()
            ),
            notificationToSupervisorTimeResolve = timeToResolve.minus(
                slaDurations.notificationToSupervisorTime.toJavaDuration()
            ),
            notificationToInitiatorCloseReminder= timeToAutoClose.minus(
                slaDurations.notificationToInitiatorCloseReminder.toJavaDuration()
            ),
            timeToAutoClose = timeToAutoClose,
            timeToSendFirstLineFromClarify = dueDateService.getDueDate(slaDurations.timeToSendFirstLineFromClarify)
        )

        log.debug { "SLA due dates for request $sdRequest, with atts $sdAtts: $dueDates" }

        return dueDates
    }

    private data class SdRequestAtts(
        val client: EntityRef,
        val priority: String
    )

}

data class SlaDueDates(
    val timeFirstReaction: Instant,
    val timeToResolve: Instant,
    val notificationToExecutorTimeReaction: Instant,
    val notificationToExecutorTimeResolve: Instant,
    val notificationToSupervisorTimeReaction: Instant,
    val notificationToSupervisorTimeResolve: Instant,
    val notificationToInitiatorCloseReminder: Instant,
    val timeToAutoClose: Instant,
    val timeToSendFirstLineFromClarify: Instant
)
