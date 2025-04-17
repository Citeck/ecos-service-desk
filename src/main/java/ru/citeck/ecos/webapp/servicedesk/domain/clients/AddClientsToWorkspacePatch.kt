package ru.citeck.ecos.webapp.servicedesk.domain.clients

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOnApps
import java.util.concurrent.Callable

@Component
@EcosPatchDependsOnApps(AppName.EMODEL)
@EcosPatch(id = "add-sd-clients-to-workspace-patch", date = "2025-04-17T00:00:00Z")
class AddClientsToWorkspacePatch(
    val clientsListener: SdClientsListener,
    val recordsService: RecordsService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val clients = recordsService.query(RecordsQuery.create()
            .withSourceId("emodel/clients-type")
            .withQuery(Predicates.alwaysTrue())
            .withMaxItems(10000)
            .build(),
            ClientsAtts::class.java
        )

        log.info { "Clients found: ${clients.getRecords().size}" }

        val processedClients = LinkedHashMap<EntityRef, List<SdClientsListener.AssocDiff>>()
        for (client in clients.getRecords()) {
            val assocsDiff = ArrayList<SdClientsListener.AssocDiff>()
            if (client.users.isNotEmpty()) {
                assocsDiff.add(SdClientsListener.AssocDiff("users", client.users, emptyList()))
            }
            if (client.authGroups.isNotEmpty()) {
                assocsDiff.add(SdClientsListener.AssocDiff("authGroups", client.authGroups, emptyList()))
            }
            TxnContext.doInNewTxn {
                clientsListener.handleEvent(SdClientsListener.EventData(assocsDiff))
            }
            processedClients[client.id] = assocsDiff
        }
        return processedClients
    }

    private class ClientsAtts(
        val id: EntityRef,
        @AttName("users[]?id!")
        val users: List<EntityRef>,
        @AttName("authGroups[]?id!")
        val authGroups: List<EntityRef>,
    )
}
