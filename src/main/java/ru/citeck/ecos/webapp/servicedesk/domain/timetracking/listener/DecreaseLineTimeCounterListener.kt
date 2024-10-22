package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class DecreaseLineTimeCounterListener(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private const val TYPE_ID = "ecos-time-tracking-type"
        private const val CLIENT_MAPPING_SOURCE_ID = "${AppName.EMODEL}/clients-mapping-type"
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
            withFilter(Predicates.eq("typeDef.id", TYPE_ID))
            withAction { event ->
                AuthContext.runAsSystem {
                    processDecreaseLineTimeCounter(event)
                }
            }
        }
    }

    private fun processDecreaseLineTimeCounter(event: EventData) {
        val clientMappingRef = findClientMappingRef(event.clientRef)
        val attRemainingTime =
            lineCounterMap[event.supportLine] ?: error("Support line ${event.supportLine} does not supported")
        val remainingTimeInMinutes = recordsService.getAtt(clientMappingRef, "$attRemainingTime?num!").asLong()
        val newRemainingTimeInMinutes = remainingTimeInMinutes - event.timeSpentInMinutes
        recordsService.mutate(
            clientMappingRef,
            mapOf(attRemainingTime to newRemainingTimeInMinutes)
        )
    }

    private fun findClientMappingRef(clientRef: EntityRef): EntityRef {
        return recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(CLIENT_MAPPING_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(Predicates.eq("client", clientRef))
            }
        ) ?: error("Client mapping ref not found by clientRef: $clientRef")
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
