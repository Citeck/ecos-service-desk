package ru.citeck.ecos.webapp.servicedesk.domain.sla.api

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SdDueDateService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaParametersProvider
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration
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

    fun getDueDates(sdRequest: EntityRef, initialTime: Instant? = null): SlaDueDates {
        if (sdRequest.isEmpty()) {
            error("Empty SD request not allowed")
        }

        val sdAtts = recordsService.getAtts(sdRequest, SdRequestAtts::class.java)
        val priority = SdPriority.from(sdAtts.priority)

        val slaDurations = slaParametersProvider.get(sdAtts.client, priority)

        val timeFirstReaction = dueDateService.getDueDate(slaDurations.timeFirstReaction, sdRequest, initialTime)
        val timeToResolve = dueDateService.getDueDate(slaDurations.timeResolve, sdRequest, initialTime)

        val timeToAutoClose = dueDateService.getDueDate(slaDurations.timeToAutoClose, sdRequest, initialTime)

        val timeToResolveFromPause = let {
            val sla2Duration = slaDurations.timeResolve
            val spentDuration = sdAtts.sla2SpentTime.toDuration(DurationUnit.MILLISECONDS)

            log.debug { "Spent time SLA 2 of $sdRequest: $spentDuration" }

            val remainingDuration = sla2Duration - spentDuration

            return@let dueDateService.getDueDate(remainingDuration, sdRequest, initialTime)
        }
        val notificationToExecutorTimeResolveFromPause = let {
            val notificationTime = timeToResolveFromPause.minus(
                slaDurations.notificationToExecutorTimeResolve.toJavaDuration()
            )
            return@let if (notificationTime.isAfter(Instant.now())) {
                notificationTime
            } else {
                null
            }
        }
        val notificationToSupervisorTimeResolveFromPause = let {
            val notificationTime = timeToResolveFromPause.minus(
                slaDurations.notificationToSupervisorTime.toJavaDuration()
            )
            return@let if (notificationTime.isAfter(Instant.now())) {
                notificationTime
            } else {
                null
            }
        }

        val dueDates = SlaDueDates(
            timeFirstReaction = timeFirstReaction,
            timeToResolve = timeToResolve,

            timeToResolveFromPause = timeToResolveFromPause,
            notificationToExecutorTimeResolveFromPause = notificationToExecutorTimeResolveFromPause,
            notificationToSupervisorTimeResolveFromPause = notificationToSupervisorTimeResolveFromPause,

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
            notificationToInitiatorCloseReminder = timeToAutoClose.minus(
                slaDurations.notificationToInitiatorCloseReminder.toJavaDuration()
            ),
            timeToAutoClose = timeToAutoClose,
            timeToSendFirstLineFromClarify = dueDateService.getDueDate(
                slaDurations.timeToSendFirstLineFromClarify, sdRequest, initialTime
            )
        )

        log.debug { "SLA due dates for request $sdRequest, with atts $sdAtts: $dueDates" }

        return dueDates
    }

    private data class SdRequestAtts(
        val client: EntityRef,
        val priority: String,

        @AttName("sla_2_spent_time")
        val sla2SpentTime: Long = 0
    )
}

data class SlaDueDates(
    val timeFirstReaction: Instant,
    val timeToResolve: Instant,

    val timeToResolveFromPause: Instant? = null,
    val notificationToExecutorTimeResolveFromPause: Instant? = null,
    val notificationToSupervisorTimeResolveFromPause: Instant? = null,

    val notificationToExecutorTimeReaction: Instant,
    val notificationToExecutorTimeResolve: Instant,
    val notificationToSupervisorTimeReaction: Instant,
    val notificationToSupervisorTimeResolve: Instant,
    val notificationToInitiatorCloseReminder: Instant,
    val timeToAutoClose: Instant,
    val timeToSendFirstLineFromClarify: Instant
)
