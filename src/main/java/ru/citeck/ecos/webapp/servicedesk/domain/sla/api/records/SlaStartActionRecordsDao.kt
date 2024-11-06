package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.role.service.RoleService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import ru.citeck.ecos.webapp.servicedesk.domain.sla.event.SlaEventEmitter

@Component
class SlaStartActionRecordsDao(
    private val slaEventEmitter: SlaEventEmitter,
    private val roleService: RoleService,
    private val typesRegistry: EcosTypesRegistry
) : AbstractRecordsDao(), ValueMutateDao<SlaStartActionRecordsDao.ActionDto> {

    companion object {
        private const val ID = "sla-start"
        const val ATT_SLA_STOPPED = "slaStopped"

        private val SD_TYPE_REF = ModelUtils.getTypeRef("sd-request-type")
        private val ACTION_ALLOWED_FOR_ROLES = listOf(
            "tech-role",
            "lead-tech-role"
        )
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActionDto): Any? {

        checkPermissions(value.recordRef)

        recordsService.mutate(
            value.recordRef,
            mapOf(
                ATT_SLA_STOPPED to false
            )
        )

        slaEventEmitter.emitStartSla2Event(
            SlaEventEmitter.SlaEvent(
                value.recordRef
            )
        )
        return ""
    }

    private fun checkPermissions(recordRef: EntityRef) {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }
        val recordType = recordsService.getAtt(
            recordRef,
            RecordConstants.ATT_TYPE + ScalarType.ID_SCHEMA
        ).asText().toEntityRef()
        if (!typesRegistry.isSubType(recordType.getLocalId(), SD_TYPE_REF.getLocalId())) {
            error(
                "This action allowed only for ${SD_TYPE_REF.getLocalId()}. " +
                "Type of provided record: ${recordType.getLocalId()}"
            )
        }
        val userRoles = roleService.getCurrentUserRoles(recordRef, recordType)
        if (ACTION_ALLOWED_FOR_ROLES.any { userRoles.contains(it) }) {
            return
        }
        error("Permission denied. You can't execute this action for record $recordRef")
    }

    data class ActionDto(
        val recordRef: EntityRef
    )
}
