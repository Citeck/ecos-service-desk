package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class DeleteTimeTrackingRecordListener(
    eventsService: EventsService
) : AbstractTimeTrackingListener<DeleteTimeTrackingRecordListener.EventData>() {

    init {
        eventsService.addListener<EventData> {
            withTransactional(true)
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(EventData::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", TIME_TRACKING_SD_TYPE_ID),
                    Predicates.notEmpty("record._parent.client")
                )
            )
            withAction { event ->
                AuthContext.runAsSystem {
                    processUpdateRemainingTime(event.clientRef, event.supportLine, event.startDate, event)
                }
            }
        }
    }

    override fun processCalculateRemainingTime(event: EventData, remainingTimeInMinutes: Long): Long {
        return remainingTimeInMinutes + event.timeSpentInMinutes
    }

    data class EventData(
        @AttName("record._parent.client?id")
        val clientRef: EntityRef,
        @AttName("record.durationInMinutes?num!")
        val timeSpentInMinutes: Long,
        @AttName("record.startDate")
        val startDate: Instant,
        @AttName("record.line")
        val supportLine: String
    )
}
