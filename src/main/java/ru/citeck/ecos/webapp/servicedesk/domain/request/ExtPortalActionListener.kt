package ru.citeck.ecos.webapp.servicedesk.domain.request

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ExtPortalActionListener(
    val eventsService: EventsService,
    val recordsService: RecordsService
) {
    companion object {

        private const val SD_CLOSE_ACTION = "sd-close-action"

        private val log = KotlinLogging.logger {}
    }

    @PostConstruct
    fun init() {
        eventsService.addListener<ActionEvent> {
            withEventType("ext-portal-user-action")
            withDataClass(ActionEvent::class.java)
            withFilter(Predicates.eq("record._type?localId", "sd-request-type"))
            withTransactional(true)
            withAction { handleEvent(it) }
        }
    }

    private fun handleEvent(event: ActionEvent) {
        log.info { "External event received: $event" }
        if (AuthContext.isRunAsSystem()) {
            error("User action can't be executed as system user")
        }
        if (event.actionType == SD_CLOSE_ACTION) {
            recordsService.mutateAtt(event.record, StatusConstants.ATT_STATUS, "request-closes")
        } else {
            error("Unknown event type: ${event.actionType}")
        }
    }

    data class ActionEvent(
        val record: EntityRef,
        val actionType: String,
        val actionData: ObjectData
    )
}
