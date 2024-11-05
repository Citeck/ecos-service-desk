package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.job

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.iter.IterableRecords
import ru.citeck.ecos.records3.iter.IterableRecordsConfig
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records.SlaStartActionRecordsDao

@Component
class ResetRemainingTimeAndStartSlaJob(
    recordsServiceFactory: RecordsServiceFactory,
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

    @Value("\${service-desk.job.time-tracking.resetRemainingTimeAndStartSla.cron}")
    private lateinit var cron: String

    private val recordsService = recordsServiceFactory.recordsService
    private val dtoSchemaReader = recordsServiceFactory.dtoSchemaReader
    private val dtoSchemaWriter = recordsServiceFactory.attSchemaWriter

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
                log.info { "ResetRemainingTimeAndStartSlaJob was skipped as a lock was not acquired." }
            }
        }
    }

    private fun sync() {
        forEachRecord(
            RecordsQuery.create {
                withSourceId(CLIENTS_MAPPING_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(
                    Predicates.and(
                        Predicates.notEmpty(ATT_CLIENT)
                    )
                )
            },
            ClientData::class.java,
            MAX_ITERATION
        ) { client ->
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
    }

    private fun startSlaForSdRequests(clientRef: EntityRef) {
        forEachRecord(
            RecordsQuery.create {
                withSourceId(SD_REQUESTS_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(
                    Predicates.and(
                        Predicates.eq(ATT_CLIENT, clientRef),
                        Predicates.eq(SlaStartActionRecordsDao.ATT_SLA_STOPPED, true),
                        Predicates.notEq(StatusConstants.ATT_STATUS, "request-closes")
                    )
                )
            },
            EntityRef::class.java,
            MAX_ITERATION
        ) {
            TxnContext.doInNewTxn {
                recordsService.mutate(
                    "service-desk/sla-start@",
                    mapOf("recordRef" to it)
                )
            }
        }
    }

    /**
     * Executes the specified action on each record that matches the given query, up to the specified iteration limit.
     *
     * @param query the query defining the records to be processed
     * @param recordType the class type of the record attributes
     * @param iterationsLimit the maximum number of records on which to execute the action
     * @param action a function to be executed for each record
     */
    private fun <T : Any> forEachRecord(
        query: RecordsQuery,
        recordType: Class<T>,
        iterationsLimit: Int,
        action: (T) -> Unit
    ) {
        val attsToLoadSchema = if (EntityRef::class.java.isAssignableFrom(recordType)) {
            emptyMap()
        } else {
            dtoSchemaWriter.writeToMap(dtoSchemaReader.read(recordType))
        }

        val records = IterableRecords(
            query, IterableRecordsConfig.create()
                .withAttsToLoad(attsToLoadSchema)
                .build(),
            recordsService
        ).iterator()

        var iterationsCount = 0
        while (records.hasNext() && iterationsCount++ < iterationsLimit) {
            val record = records.next()
            val attsInstance = if (EntityRef::class.java.isAssignableFrom(recordType)) {
                @Suppress("UNCHECKED_CAST")
                record.getId() as T
            } else {
                dtoSchemaReader.instantiateNotNull(recordType, record.getAtts())
            }
            action.invoke(attsInstance)
        }
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
