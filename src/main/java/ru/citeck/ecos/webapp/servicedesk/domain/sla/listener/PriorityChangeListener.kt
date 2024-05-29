package ru.citeck.ecos.webapp.servicedesk.domain.sla.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager

@Component
class PriorityChangeListener(
    eventsService: EventsService,
    private val slaManager: SlaManager,
    private val recordsService: RecordsService
) {
    companion object {
        private const val PRIORITY_CHANGED_ATT = "priorityHasBeenChanged"
        private const val SLA_1_DUE_DATE_ATT = "sla_1_due_date"
        private const val SLA_2_DUE_DATE_ATT = "sla_2_due_date"
        private const val BPMN_PROC_SOURCE_ID = "eproc/bpmn-proc"
        private const val BPMN_JOB_SOURCE_ID = "eproc/bpmn-job"
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
            withAction { document ->

                AuthContext.runAsSystem {
                    recordsService.mutate(document.ref, mapOf(PRIORITY_CHANGED_ATT to true))
                }

                val recordSla = slaManager.getDueDates(document.ref)

                AuthContext.runAsSystem {
                    recordsService.mutate(
                        document.ref, mapOf(
                            SLA_1_DUE_DATE_ATT to recordSla.timeFirstReaction,
                            SLA_2_DUE_DATE_ATT to recordSla.timeToResolve
                        )
                    )
                }

                val documentProcess = document.ref.getActiveProcess()
                if (documentProcess.isEmpty()) {
                    return@withAction
                }

                val timersRefs = documentProcess.getJobs()

                val timersToValue = mapOf(
                    "Event_1oke406" to recordSla.notificationToSupervisorTimeResolve,
                    "Event_0mvrxmh" to recordSla.notificationToExecutorTimeResolve,
                    "Event_0j6gdz4" to recordSla.timeToSendFirstLineFromClarify,
                    "Event_0td1d3a" to recordSla.notificationToInitiatorCloseReminder,
                    "Event_0mgqtus" to recordSla.timeToAutoClose,
                    "Event_1q9mnfb" to recordSla.notificationToSupervisorTimeReaction,
                    "Event_0zz32r2" to recordSla.notificationToExecutorTimeReaction
                )

                for ((elementId, value) in timersToValue) {
                    timersRefs.find { it.getActivityId() == elementId }?.let { timer ->
                        AuthContext.runAsSystem {
                            recordsService.mutate(timer, mapOf("dueDate" to value))
                        }
                    }
                }
            }
        }
    }

    private fun EntityRef.getActivityId(): String {
        return recordsService.getAtts(this, TimerAtts::class.java).activityId
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

    private fun EntityRef.getJobs(): List<EntityRef> {
        return recordsService.query(
            RecordsQuery.create()
                .withSourceId(BPMN_JOB_SOURCE_ID)
                .withQuery(
                    Predicates.eq("bpmnProcess", this)
                )
                .withSortBy(SortBy("dueDate", false))
                .withPage(
                    QueryPage.create()
                        .withMaxItems(30)
                        .withSkipCount(0)
                        .build()
                )
                .build()
        ).getRecords()
    }
}

data class EventData(
    @AttName("record?id")
    val ref: EntityRef,
    @AttName("record.priority?str")
    val priority: String
)

data class TimerAtts(
    @AttName("jobDefinition.activityId")
    val activityId: String
)
