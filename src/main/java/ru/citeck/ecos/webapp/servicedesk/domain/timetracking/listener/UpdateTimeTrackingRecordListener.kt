package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.timetracking.service.SdTimeTrackingService
import java.time.Instant

@Component
class UpdateTimeTrackingRecordListener(
    private val sdTimeTrackingService: SdTimeTrackingService,
    eventsService: EventsService
) {

    companion object {
        private const val ATT_START_DATE = "startDate"
        private const val ATT_DURATION = "duration"
        private const val ATT_LINE = "line"
    }

    init {
        eventsService.addListener<EventData> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(EventData::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", SdTimeTrackingService.TIME_TRACKING_SD_TYPE_ID),
                    Predicates.notEmpty("record._parent.client"),
                    Predicates.or(
                        Predicates.eq("diff._has.duration?bool", true),
                        Predicates.eq("diff._has.line?bool", true),
                        Predicates.eq("diff._has.startDate?bool", true)
                    )
                )
            )
            withAction { event ->
                AuthContext.runAsSystem {
                    val clientMappingRef = sdTimeTrackingService.findClientMappingRef(event.clientRef)
                    clientMappingRef?.let {
                        val updatedRemainingTimeMap =
                            sdTimeTrackingService.getRemainingTimeMap(clientMappingRef).toMutableMap()

                        var beforeStartDate = event.startDate
                        var afterStartDate = event.startDate

                        val currentDurationInMinutes = event.duration / 60000
                        var beforeDuration = currentDurationInMinutes
                        var afterDuration = currentDurationInMinutes

                        val currentAttRemainingTime = sdTimeTrackingService.getAttRemainingTime(event.supportLine)
                        var beforeAttRemainingTime = currentAttRemainingTime
                        var afterAttRemainingTime = currentAttRemainingTime

                        event.diff.forEach {
                            val diffValue = it.getAs(RecordChangedEvent.DiffValue::class.java)
                            if (diffValue != null) {
                                when (diffValue.id) {
                                    ATT_START_DATE -> {
                                        beforeStartDate = Instant.parse(diffValue.before as String)
                                        afterStartDate = Instant.parse(diffValue.after as String)
                                    }

                                    ATT_DURATION -> {
                                        beforeDuration = (diffValue.before as Number).toLong() / 60000
                                        afterDuration = (diffValue.after as Number).toLong() / 60000
                                    }

                                    ATT_LINE -> {
                                        beforeAttRemainingTime =
                                            sdTimeTrackingService.getAttRemainingTime(diffValue.before as String)
                                        afterAttRemainingTime =
                                            sdTimeTrackingService.getAttRemainingTime(diffValue.after as String)
                                    }
                                }
                            }
                        }

                        if (sdTimeTrackingService.isCurrentMonthDate(beforeStartDate)) {
                            updatedRemainingTimeMap[beforeAttRemainingTime]?.let {
                                updatedRemainingTimeMap[beforeAttRemainingTime] = it + beforeDuration
                            }
                        }

                        if (sdTimeTrackingService.isCurrentMonthDate(afterStartDate)) {
                            updatedRemainingTimeMap[afterAttRemainingTime]?.let {
                                updatedRemainingTimeMap[afterAttRemainingTime] = it - afterDuration
                            }
                        }

                        sdTimeTrackingService.mutateRemainingTime(
                            clientMappingRef,
                            updatedRemainingTimeMap
                        )
                    }
                }
            }
        }
    }

    data class EventData(
        @AttName("record._parent.client?id")
        val clientRef: EntityRef,
        @AttName("record?id")
        val recordRef: EntityRef,
        @AttName("diff.list[]")
        val diff: List<ObjectData>,
        @AttName("record.duration?num")
        val duration: Long,
        @AttName("record.startDate")
        val startDate: Instant,
        @AttName("record.line")
        val supportLine: String
    )
}
