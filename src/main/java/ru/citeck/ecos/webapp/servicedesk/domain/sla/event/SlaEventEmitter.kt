package ru.citeck.ecos.webapp.servicedesk.domain.sla.event

import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class SlaEventEmitter(
    eventsService: EventsService
) {

    companion object {
        private const val START_SLA2_EVENT = "sla-2-start"
    }

    private val startSla2Emitter = eventsService.getEmitter(
        EmitterConfig.create<SlaEvent> {
            withSource(SlaEvent::class.simpleName)
            withEventType(START_SLA2_EVENT)
            withEventClass(SlaEvent::class.java)
        }
    )

    fun emitStartSla2Event(event: SlaEvent) {
        startSla2Emitter.emit(event)
    }

    data class SlaEvent(
        val record: EntityRef,
        val recordType: EntityRef = EntityRef.valueOf("emodel/type@sd-request-type")
    )
}
