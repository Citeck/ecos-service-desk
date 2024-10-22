package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.job

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import java.time.Duration

@Component
class ResetRemainingTimeAndStartSlaJob(
    private val recordsService: RecordsService,
    private val taskScheduler: EcosTaskSchedulerApi,
    private val appLockService: EcosAppLockService
) {

    companion object {
        private const val MAX_ITERATION = 10_000
        private const val CLIENTS_MAPPING_SOURCE_ID = "${AppName.EMODEL}/clients-mapping-type"
        private const val SD_REQUESTS_SOURCE_ID = "${AppName.EMODEL}/sd-request-type"
        private const val ATT_CLIENT = "client"
        private const val ATT_SLA_PAUSED = "slaPaused"
    }

    @Value("\${ecos.job.time-tracking.resetRemainingTimeAndStartSla.cron}")
    private lateinit var cron: String

    @PostConstruct
    fun init() {
        taskScheduler.schedule(
            "ResetRemainingTimeAndStartSlaJob",
            Schedules.cron(cron)
        ) {
            appLockService.doInSyncOrSkip(
                "ResetRemainingTimeAndStartSlaJob",
                Duration.ofSeconds(10)
            ) { sync() }
        }
    }

    private fun sync() {
        var iter = 0
        var clients = getClients()
        while (clients.isNotEmpty() && iter < MAX_ITERATION) {
            for (client in clients) {
                recordsService.mutate(
                    client.clientMapping,
                    mapOf(
                        "remainingTimeFirstLineSupport" to null,
                        "remainingTimeSecondLineSupport" to null,
                        "remainingTimeThirdLineSupport" to null,
                    )
                )
                startSlaForSdRequests(client.clientRef)
            }
            clients = getClients(clients.size)
            iter++
        }
    }

    private fun getClients(skipCount: Int = 0): List<ClientData> {
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(CLIENTS_MAPPING_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                addSort(SortBy(RecordConstants.ATT_CREATED, true))
                withSkipCount(skipCount)
                withMaxItems(100)
            },
            ClientData::class.java
        ).getRecords()
    }

    private fun startSlaForSdRequests(clientRef: EntityRef) {
        var sdRequestRefs = getSdRequestRefs(clientRef)
        while (sdRequestRefs.isNotEmpty()) {
            for (sdRequestRef in sdRequestRefs) {
                recordsService.mutate(
                    "service-desk/sla-start@",
                    mapOf(
                        "recordRef" to sdRequestRef
                    )
                )
            }
            sdRequestRefs = getSdRequestRefs(clientRef)
        }
    }

    private fun getSdRequestRefs(clientRef: EntityRef): List<EntityRef> {
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(SD_REQUESTS_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(
                    Predicates.and(
                        Predicates.eq(ATT_CLIENT, clientRef),
                        Predicates.eq(ATT_SLA_PAUSED, true),
                        Predicates.notEq(StatusConstants.ATT_STATUS, "request-closes")
                    )
                )
                addSort(SortBy(RecordConstants.ATT_CREATED, true))
                withMaxItems(100)
            }
        ).getRecords()
    }

    private data class ClientData(
        @AttName("id")
        val clientMapping: EntityRef,
        @AttName("client?id")
        val clientRef: EntityRef
    )
}
