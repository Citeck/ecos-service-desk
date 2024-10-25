package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.timetracking.job.ResetRemainingTimeAndStartSlaJob

@Component
class DecreaseLineTimeCounterListener(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val TIME_TRACKING_TYPE_ID = "ecos-time-tracking-type"
    }

    private val lineCounterMap = mapOf(
        "first-line" to "remainingTimeFirstLineSupport",
        "second-line" to "remainingTimeSecondLineSupport",
        "third-line" to "remainingTimeThirdLineSupport",
    )

    init {
        eventsService.addListener<EventData> {
            withTransactional(true)
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(EventData::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", TIME_TRACKING_TYPE_ID),
                    Predicates.eq("record._aspects._has.sd-support-line?bool!", true)
                )
            )
            withAction { event ->
                AuthContext.runAsSystem {
                    processDecreaseLineTimeCounter(event)
                }
            }
        }
    }

    private fun processDecreaseLineTimeCounter(event: EventData) {
        val clientMappingRef = findClientMappingRef(event.clientRef)
        if (clientMappingRef == null) {
            log.warn { "Client mapping ref not found by clientRef: ${event.clientRef}. Time counter doesn't decrease" }
        } else {
            val attRemainingTime = lineCounterMap[event.supportLine]
                ?: error("Support line ${event.supportLine} does not supported")
            val remainingTime = recordsService.getAtt(clientMappingRef, "$attRemainingTime?num")
            if (remainingTime.isNull()) {
                log.warn { "Time limit for line=${event.supportLine} are not set in the client ${event.clientRef}" }
            } else {
                val remainingTimeInMinutes = remainingTime.asLong()
                val newRemainingTimeInMinutes = remainingTimeInMinutes - event.timeSpentInMinutes
                recordsService.mutate(
                    clientMappingRef,
                    mapOf(attRemainingTime to newRemainingTimeInMinutes)
                )
            }
        }
    }

    private fun findClientMappingRef(clientRef: EntityRef): EntityRef? {
        return recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(ResetRemainingTimeAndStartSlaJob.CLIENTS_MAPPING_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(Predicates.eq("client", clientRef))
            }
        )
    }

    private data class EventData(
        @AttName("record._parent.client?id")
        val clientRef: EntityRef,
        @AttName("record.durationInMinutes?num!")
        val timeSpentInMinutes: Long,
        @AttName("record.sd-support-line:line")
        val supportLine: String
    )
}
