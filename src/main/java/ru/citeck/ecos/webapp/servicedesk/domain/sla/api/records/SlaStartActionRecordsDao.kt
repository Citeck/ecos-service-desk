package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.sla.event.SlaEventEmitter

@Component
class SlaStartActionRecordsDao(
    private val slaEventEmitter: SlaEventEmitter
) : AbstractRecordsDao(), ValueMutateDao<SlaStartActionRecordsDao.ActionDto> {

    companion object {
        private const val ID = "sla-start"
        private const val SLA_PAUSED_ATT = "slaPaused"
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActionDto): Any? {
        AuthContext.runAsSystem {
            recordsService.mutate(
                value.recordRef,
                mapOf(
                    SLA_PAUSED_ATT to false
                )
            )

            slaEventEmitter.emitStartSla2Event(
                SlaEventEmitter.SlaEvent(
                    value.recordRef
                )
            )
        }
        return ""
    }

    data class ActionDto(
        val recordRef: EntityRef
    )
}
