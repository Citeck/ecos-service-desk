package ru.citeck.ecos.webapp.servicedesk.domain.sla.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager
import java.time.Instant

@Component
class PriorityChangeListener(
    eventsService: EventsService,
    private val slaManager: SlaManager,
    private val recordsService: RecordsService
) {
    companion object {
        private const val SLA_1_DUE_DATE_ATT = "sla_1_due_date"
        private const val SLA_2_DUE_DATE_ATT = "sla_2_due_date"
        private const val BPMN_PROC_SOURCE_ID = "eproc/bpmn-proc"
        private const val BPMN_JOB_SOURCE_ID = "eproc/bpmn-job"

        private val log = KotlinLogging.logger {}
    }

    init {
        eventsService.addListener<EventData> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(EventData::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("diff._has.priority?bool!", true),
                    Predicates.eq("record._type?id", "emodel/type@sd-request-type")
                )
            )
            withTransactional(true)
            withAction { sdRequest ->
                AuthContext.runAsSystem {
                    updateSlaAndTimersDueDates(sdRequest)
                }

            }
        }
    }

    private fun updateSlaAndTimersDueDates(sdRequestEvent: EventData) {
        val sdProcesses = sdRequestEvent.ref.getActiveProcess()
        if (sdProcesses.isEmpty()) {
            return
        }

        val dueDateForSla = slaManager.getDueDates(sdRequestEvent.ref, sdRequestEvent.created)

        val updatedSla = mapOf(
            SLA_1_DUE_DATE_ATT to dueDateForSla.timeFirstReaction,
            SLA_2_DUE_DATE_ATT to dueDateForSla.timeToResolve
        )
        log.info { "Update SLA due dates for ${sdRequestEvent.ref} to $updatedSla" }
        recordsService.mutate(sdRequestEvent.ref, updatedSla)

        sdProcesses.getTimerAtts().forEach { foundTimer ->
            val timerCreatedAt = foundTimer.created
            val dueDatesForTime = slaManager.getDueDates(sdRequestEvent.ref, timerCreatedAt)

            val newDueDate = dueDatesForTime.getSlaForTimerId(foundTimer.activityId)

            log.info {
                "Update SD ${sdRequestEvent.ref} timer ${foundTimer.activityId} " +
                        "due date from ${foundTimer.dueDate} to $newDueDate"
            }

            recordsService.mutate(foundTimer.ref, mapOf("dueDate" to newDueDate))
        }
    }

    // It's important to keep Timers IDs in sync with the BPMN sd-process.bpmn.xml
    private fun SlaDueDates.getSlaForTimerId(timerId: String): Instant? {
        return when (timerId) {
            "Event_1oke406" -> notificationToSupervisorTimeResolve
            "Event_0mvrxmh" -> notificationToExecutorTimeResolve
            "Event_0j6gdz4" -> timeToSendFirstLineFromClarify
            "Event_0td1d3a" -> notificationToInitiatorCloseReminder
            "Event_0mgqtus" -> timeToAutoClose
            "Event_1q9mnfb" -> notificationToSupervisorTimeReaction
            "Event_0zz32r2" -> notificationToExecutorTimeReaction
            else -> null
        }
    }

    private fun EntityRef.getActiveProcess(): EntityRef {
        return recordsService.queryOne(
            RecordsQuery.create()
                .withSourceId(BPMN_PROC_SOURCE_ID)
                .withQuery(
                    Predicates.eq("document", this)
                )
                .withSortBy(SortBy("startTime", false))
                .withPage(
                    QueryPage.create()
                        .withMaxItems(30)
                        .withSkipCount(0)
                        .build()
                )
                .build()
        ).toEntityRef()
    }

    private fun EntityRef.getTimerAtts(): List<TimerAtts> {
        return recordsService.query(
            RecordsQuery.create()
                .withSourceId(BPMN_JOB_SOURCE_ID)
                .withQuery(
                    Predicates.eq("bpmnProcess", this)
                )
                .withSortBy(SortBy("dueDate", false))
                .withPage(
                    QueryPage.create()
                        .withMaxItems(50)
                        .withSkipCount(0)
                        .build()
                )
                .build(),
            TimerAtts::class.java
        ).getRecords()
    }
}

private data class EventData(
    @AttName("record?id")
    val ref: EntityRef,
    @AttName("record.priority?str")
    val priority: String,
    @AttName("record._created")
    val created: Instant
)

private data class TimerAtts(
    @AttName(".id")
    val ref: EntityRef,

    @AttName("jobDefinition.activityId")
    val activityId: String,

    @AttName(RecordConstants.ATT_CREATED)
    val created: Instant,

    @AttName("dueDate")
    val dueDate: Instant
)
