package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.job

import io.github.oshai.kotlinlogging.KotlinLogging
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
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records.SlaStartActionRecordsDao

@Component
class ResetRemainingTimeAndStartSlaJob(
    private val recordsService: RecordsService,
    private val taskScheduler: EcosTaskSchedulerApi,
    private val appLockService: EcosAppLockService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val MAX_ITERATION = 10_000
        const val CLIENTS_MAPPING_SOURCE_ID = "${AppName.EMODEL}/clients-mapping-type"
        private const val SD_REQUESTS_SOURCE_ID = "${AppName.EMODEL}/sd-request-type"
        private const val ATT_CLIENT = "client"

        private const val ATT_TIME_LIMIT_FIRST_LINE_SUPPORT = "timeLimitFirstLineSupport"
        private const val ATT_TIME_LIMIT_SECOND_LINE_SUPPORT = "timeLimitSecondLineSupport"
        private const val ATT_TIME_LIMIT_THIRD_LINE_SUPPORT = "timeLimitThirdLineSupport"

        private const val ATT_REMAINING_TIME_FIRST_LINE_SUPPORT = "remainingTimeFirstLineSupport"
        private const val ATT_REMAINING_TIME_SECOND_LINE_SUPPORT = "remainingTimeSecondLineSupport"
        private const val ATT_REMAINING_TIME_THIRD_LINE_SUPPORT = "remainingTimeThirdLineSupport"
    }

    @Value("\${ecos.job.time-tracking.resetRemainingTimeAndStartSla.cron}")
    private lateinit var cron: String

    @PostConstruct
    fun init() {
        taskScheduler.schedule(
            "ResetRemainingTimeAndStartSlaJob",
            Schedules.cron(cron)
        ) {
            val isExecuted = appLockService.doInSyncOrSkip(
                "ResetRemainingTimeAndStartSlaJob"
            ) { sync() }

            if (!isExecuted) {
                log.error { "ResetRemainingTimeAndStartSlaJob failed to lock app and was skipped" }
            }
        }
    }

    private fun sync() {
        var iter = 0
        var clients = getClients()
        while (clients.isNotEmpty() && iter < MAX_ITERATION) {
            for (client in clients) {
                val attsToMutate = mutableMapOf<String, Long?>(
                    ATT_REMAINING_TIME_FIRST_LINE_SUPPORT to null,
                    ATT_REMAINING_TIME_SECOND_LINE_SUPPORT to null,
                    ATT_REMAINING_TIME_THIRD_LINE_SUPPORT to null,
                )

                if (client.timeLimitFirstLineSupport != null) {
                    attsToMutate[ATT_REMAINING_TIME_FIRST_LINE_SUPPORT] = client.timeLimitFirstLineSupport * 60
                }
                if (client.timeLimitSecondLineSupport != null) {
                    attsToMutate[ATT_REMAINING_TIME_SECOND_LINE_SUPPORT] = client.timeLimitSecondLineSupport * 60
                }
                if (client.timeLimitThirdLineSupport != null) {
                    attsToMutate[ATT_REMAINING_TIME_THIRD_LINE_SUPPORT] = client.timeLimitThirdLineSupport * 60
                }

                recordsService.mutate(
                    client.clientMapping,
                    attsToMutate
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
                withQuery(
                    Predicates.and(
                        Predicates.notEmpty(ATT_CLIENT),
                        Predicates.or(
                            Predicates.notEmpty(ATT_TIME_LIMIT_FIRST_LINE_SUPPORT),
                            Predicates.notEmpty(ATT_TIME_LIMIT_SECOND_LINE_SUPPORT),
                            Predicates.notEmpty(ATT_TIME_LIMIT_THIRD_LINE_SUPPORT)
                        )
                    )
                )
                addSort(SortBy(RecordConstants.ATT_CREATED, true))
                withSkipCount(skipCount)
                withMaxItems(100)
            },
            ClientData::class.java
        ).getRecords()
    }

    private fun startSlaForSdRequests(clientRef: EntityRef) {
        var iter = 0
        var sdRequestRefs = getSdRequestRefs(clientRef)
        while (sdRequestRefs.isNotEmpty() && iter < MAX_ITERATION) {
            TxnContext.doInNewTxn {
                for (sdRequestRef in sdRequestRefs) {
                    recordsService.mutate(
                        "service-desk/sla-start@",
                        mapOf(
                            "recordRef" to sdRequestRef
                        )
                    )
                }
            }
            sdRequestRefs = getSdRequestRefs(clientRef)
            iter++
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
                        Predicates.eq(SlaStartActionRecordsDao.ATT_SLA_PAUSED, true),
                        Predicates.notEq(StatusConstants.ATT_STATUS, "request-closes")
                    )
                )
                addSort(SortBy(RecordConstants.ATT_CREATED, true))
                withMaxItems(5)
            }
        ).getRecords()
    }

    private data class ClientData(
        @AttName("?id")
        val clientMapping: EntityRef,
        @AttName("client?id")
        val clientRef: EntityRef,
        @AttName("$ATT_TIME_LIMIT_FIRST_LINE_SUPPORT?num")
        val timeLimitFirstLineSupport: Long?,
        @AttName("$ATT_TIME_LIMIT_SECOND_LINE_SUPPORT?num")
        val timeLimitSecondLineSupport: Long?,
        @AttName("$ATT_TIME_LIMIT_THIRD_LINE_SUPPORT?num")
        val timeLimitThirdLineSupport: Long?
    )
}
