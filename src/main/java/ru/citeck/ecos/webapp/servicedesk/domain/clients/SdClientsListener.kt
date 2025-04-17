package ru.citeck.ecos.webapp.servicedesk.domain.clients

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class SdClientsListener(
    val eventsService: EventsService,
    val recordsService: RecordsService
) {

    companion object {
        private const val CLIENT_ATT_USERS = "users"
        private const val CLIENT_ATT_AUTH_GROUPS = "authGroups"

        private const val MEMBER_SRC_ID = "emodel/workspace-member"
        private const val MEMBER_ATT_MEMBER_ID = "memberId"
        private const val MEMBER_ATT_AUTHORITIES = "authorities"
        private const val MEMBER_ATT_ROLE = "memberRole"
        private const val MEMBER_ROLE_USER = "USER"

        private const val WS_ATT_MEMBERS = "workspaceMembers"

        private const val ATT_ADD_PREFIX = "att_add_"
        private const val ATT_REM_PREFIX = "att_rem_"

        private const val USERS_MEMBER_ID = "SD_CLIENTS_USERS"
        private const val GROUPS_MEMBER_ID = "SD_CLIENTS_GROUPS"

        private val SD_WORKSPACE_REF = EntityRef.create(
            AppName.EMODEL,
            "workspace",
            "service-desk-workspace"
        )
    }

    @PostConstruct
    fun init() {
        listOf(RecordCreatedEvent.TYPE, RecordChangedEvent.TYPE).forEach { eventType ->

            val filter = AndPredicate.of(Predicates.eq("typeDef.id", "clients-type"))
            if (eventType == RecordChangedEvent.TYPE) {
                filter.addPredicate(
                    Predicates.or(
                        Predicates.eq("diff._has.$CLIENT_ATT_USERS?bool", true),
                        Predicates.eq("diff._has.$CLIENT_ATT_AUTH_GROUPS?bool", true)
                    )
                )
            }

            eventsService.addListener<EventData> {
                withEventType(eventType)
                withDataClass(EventData::class.java)
                withFilter(filter)
                withTransactional(true)
                withAction { handleEvent(it) }
            }
        }
    }

    fun handleEvent(event: EventData) {
        event.assocs ?: return
        AuthContext.runAsSystem {
            event.assocs.find { it.assocId == CLIENT_ATT_USERS }?.let {
                handleField(USERS_MEMBER_ID, it.added, it.removed)
            }
            event.assocs.find { it.assocId == CLIENT_ATT_AUTH_GROUPS }?.let {
                handleField(GROUPS_MEMBER_ID, it.added, it.removed)
            }
        }
    }

    private fun handleField(memberId: String, added: List<EntityRef>, removed: List<EntityRef>) {

        if (added.isEmpty() && removed.isEmpty()) {
            return
        }

        val existingMemberRef = recordsService.queryOne(
            RecordsQuery.create()
                .withSourceId(MEMBER_SRC_ID)
                .withQuery(
                    Predicates.and(
                        Predicates.eq(RecordConstants.ATT_PARENT, SD_WORKSPACE_REF),
                        Predicates.eq(MEMBER_ATT_MEMBER_ID, memberId)
                    )
                ).build()
        ) ?: EntityRef.EMPTY

        val memberMutAtts = RecordAtts()
        if (existingMemberRef.isEmpty()) {
            if (added.isEmpty()) {
                return
            }
            memberMutAtts.setId(EntityRef.create(MEMBER_SRC_ID, ""))
            memberMutAtts.setAtt(MEMBER_ATT_MEMBER_ID, memberId)
            memberMutAtts.setAtt(MEMBER_ATT_AUTHORITIES, added)
            memberMutAtts.setAtt(MEMBER_ATT_ROLE, MEMBER_ROLE_USER)
            memberMutAtts.setAtt(RecordConstants.ATT_PARENT, SD_WORKSPACE_REF)
            memberMutAtts.setAtt(RecordConstants.ATT_PARENT_ATT, WS_ATT_MEMBERS)
        } else {
            memberMutAtts.setId(existingMemberRef)
            if (added.isNotEmpty()) {
                memberMutAtts.setAtt(ATT_ADD_PREFIX + MEMBER_ATT_AUTHORITIES, added)
            }
            if (removed.isNotEmpty()) {
                memberMutAtts.setAtt(ATT_REM_PREFIX + MEMBER_ATT_AUTHORITIES, removed)
            }
        }
        val mutRes = recordsService.mutate(memberMutAtts)
        if (existingMemberRef.isNotEmpty()
            && removed.isNotEmpty() && added.isEmpty()
            && !recordsService.getAtt(mutRes, "_has.$MEMBER_ATT_AUTHORITIES?bool").asBoolean()
        ) {
            recordsService.delete(mutRes)
        }
    }

    class EventData(
        val assocs: List<AssocDiff>?
    )

    class AssocDiff(
        @AttName("assocId!")
        val assocId: String,
        @AttName("added[]!")
        val added: List<EntityRef>,
        @AttName("removed[]!")
        val removed: List<EntityRef>,
    )
}
