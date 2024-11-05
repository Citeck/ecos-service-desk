package ru.citeck.ecos.webapp.servicedesk.domain.timetracking.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.timetracking.job.ResetRemainingTimeAndStartSlaJob
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Component
abstract class AbstractTimeTrackingListener<T : Any> {

    companion object {
        private val log = KotlinLogging.logger {}

        const val TIME_TRACKING_SD_TYPE_ID = "ecos-time-tracking-sd-type"
    }

    private val lineCounterMap = mapOf(
        "first-line" to "remainingTimeFirstLineSupport",
        "second-line" to "remainingTimeSecondLineSupport",
        "third-line" to "remainingTimeThirdLineSupport",
    )

    @Autowired
    private lateinit var recordsService: RecordsService

    protected fun processUpdateRemainingTime(
        clientRef: EntityRef,
        supportLine: String,
        timeTrackingDate: Instant,
        event: T
    ) {
        val zoneId = ZoneId.systemDefault()
        val startYearMonth = YearMonth.from(timeTrackingDate.atZone(zoneId))
        val currentYearMonth = YearMonth.now(zoneId)
        if (startYearMonth != currentYearMonth) {
            log.info { "Time counter doesn't update for client: ${clientRef}. Time spent not in the current month" }
            return
        }

        val clientMappingRef = findClientMappingRef(clientRef)
        clientMappingRef?.let {
            val attRemainingTime = getAttRemainingTime(supportLine)
            val remainingTimeInMinutes = getRemainingTime(clientMappingRef, attRemainingTime)
            remainingTimeInMinutes?.let {
                val newRemainingTimeInMinutes = processCalculateRemainingTime(event, remainingTimeInMinutes)
                recordsService.mutate(
                    clientMappingRef,
                    mapOf(attRemainingTime to newRemainingTimeInMinutes)
                )
            }
        }
    }

    protected abstract fun processCalculateRemainingTime(event: T, remainingTimeInMinutes: Long): Long

    private fun findClientMappingRef(clientRef: EntityRef): EntityRef? {
        val clientMappingRef = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(ResetRemainingTimeAndStartSlaJob.CLIENTS_MAPPING_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(Predicates.eq("client", clientRef))
            }
        )
        if (clientMappingRef == null) {
            log.warn { "Client mapping ref not found by clientRef: ${clientRef}. Time counter doesn't update" }
            return null
        }
        return clientMappingRef
    }

    private fun getAttRemainingTime(supportLine: String): String {
        return lineCounterMap[supportLine] ?: error("Support line $supportLine does not supported")
    }

    private fun getRemainingTime(clientMappingRef: EntityRef, attRemainingTime: String): Long? {
        val remainingTime = recordsService.getAtt(clientMappingRef, "$attRemainingTime?num")
        if (remainingTime.isNull()) {
            log.warn { "Time limit for line = $attRemainingTime are not set in the client $clientMappingRef" }
            return null
        }
        return remainingTime.asLong()
    }
}
