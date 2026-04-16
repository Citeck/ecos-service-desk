package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class SdClientsMappingCacheListener(
    private val eventsService: EventsService,
    private val cache: SdClientsMappingRefCache
) {

    companion object {
        private const val MAPPING_TYPE_ID = "clients-mapping-type"
    }

    @PostConstruct
    fun init() {
        val typeFilter = Predicates.eq("typeDef.id", MAPPING_TYPE_ID)

        eventsService.addListener<CreatedOrDeletedEventData> {
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(CreatedOrDeletedEventData::class.java)
            withFilter(typeFilter)
            withTransactional(false)
            withExclusive(false)
            withAction { data -> handleCreatedOrDeleted(data) }
        }

        eventsService.addListener<CreatedOrDeletedEventData> {
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(CreatedOrDeletedEventData::class.java)
            withFilter(typeFilter)
            withTransactional(false)
            withExclusive(false)
            withAction { data -> handleCreatedOrDeleted(data) }
        }

        eventsService.addListener<ChangedEventData> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(ChangedEventData::class.java)
            withFilter(
                Predicates.and(
                    typeFilter,
                    Predicates.eq("diff._has.client?bool", true)
                )
            )
            withTransactional(false)
            withExclusive(false)
            withAction { data -> handleChanged(data) }
        }
    }

    private fun handleCreatedOrDeleted(data: CreatedOrDeletedEventData) {
        AuthContext.runAsSystem {
            invalidateByClient(data.client)
        }
    }

    private fun handleChanged(data: ChangedEventData) {
        AuthContext.runAsSystem {
            invalidateByClient(data.beforeClient)
            if (data.beforeClient != data.afterClient) {
                invalidateByClient(data.afterClient)
            }
        }
    }

    private fun invalidateByClient(clientRef: EntityRef?) {
        if (clientRef == null || EntityRef.isEmpty(clientRef)) {
            cache.invalidateAll()
        } else {
            cache.invalidate(clientRef.toString())
        }
    }

    class CreatedOrDeletedEventData(
        @AttName("record.client?id")
        val client: EntityRef?
    )

    class ChangedEventData(
        @AttName("before.client?id")
        val beforeClient: EntityRef?,
        @AttName("after.client?id")
        val afterClient: EntityRef?
    )
}
