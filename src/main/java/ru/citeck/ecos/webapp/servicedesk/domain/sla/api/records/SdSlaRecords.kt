package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaParametersProvider
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaState
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaType
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration

@Component
class SdSlaParamsRecords(
    private val slaManager: SlaManager
) : AbstractRecordsDao(), RecordsQueryDao {
    override fun getId(): String {
        return "sd-sla-params"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val record = recsQuery.getQuery(SlaQuery::class.java).record

        val dueDate = slaManager.getDueDates(record)

        val result = RecsQueryRes<SlaDueDates>()
        result.setRecords(listOf(dueDate))

        return result
    }
}

@Component
class SdSlaRecords(
    private val slaParametersProvider: SlaParametersProvider
) : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun getId(): String {
        return "sd-sla"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val record = recsQuery.getQuery(SlaQuery::class.java).record
        if (record.isEmpty()) {
            log.warn { "Empty SD request not allowed" }
            return null
        }

        val req = recordsService.getAtts(record, RequestInfo::class.java)
        if (req.client == EntityRef.EMPTY || req.priority == null) {
            return null
        }

        val sla1 = req.toSlaInfo(SlaType.SLA1)
        val sla2 = req.toSlaInfo(SlaType.SLA2)

        log.debug { "SLA info for $record: \nsla1: $sla1 \nsla2:$sla2" }

        val result = RecsQueryRes<SlaRecord>()
        result.setRecords(
            listOf(
                SlaRecord(
                    sla1Info = sla1,
                    sla2Info = sla2
                )
            )
        )
        return result
    }

    private fun RequestInfo.toSlaInfo(type: SlaType): SlaInfo {
        return when (type) {
            SlaType.SLA1 -> {
                return when (sla1State) {
                    SlaState.CREATED -> SlaInfo(
                        state = SlaState.CREATED,
                        duration = Duration.ZERO.toIsoString()
                    )

                    SlaState.RUNNING -> run {
                        if (sla1DueDate == null) {
                            log.warn { "SLA 1 is running, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val now = Instant.now()
                        val diffDuration = java.time.Duration.between(now, sla1DueDate).toKotlinDuration()

                        SlaInfo(
                            state = SlaState.RUNNING,
                            duration = diffDuration.toIsoString()
                        )
                    }

                    SlaState.COMPLETE -> run {
                        if (sla1DueDate == null) {
                            log.warn { "SLA 1 is complete, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        if (sla1CompletedAt == null) {
                            log.warn { "SLA 1 is complete, but complete date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val diffDuration = java.time.Duration.between(sla1CompletedAt, sla1DueDate).toKotlinDuration()

                        SlaInfo(
                            state = SlaState.COMPLETE,
                            duration = diffDuration.toIsoString()
                        )
                    }

                    else -> {
                        log.warn { "SLA 1 unsupported state. $this" }
                        SlaInfo.UNDEFINED
                    }
                }
            }

            SlaType.SLA2 -> {
                return when (sla2State) {
                    SlaState.CREATED -> SlaInfo(
                        state = SlaState.CREATED,
                        duration = Duration.ZERO.toIsoString()
                    )

                    SlaState.RUNNING -> run {
                        if (sla2DueDate == null) {
                            log.warn { "SLA 2 is running, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val now = Instant.now()
                        val diffDuration = java.time.Duration.between(now, sla2DueDate).toKotlinDuration()

                        SlaInfo(
                            state = SlaState.RUNNING,
                            duration = diffDuration.toIsoString()
                        )
                    }

                    SlaState.COMPLETE -> run {
                        if (sla2DueDate == null) {
                            log.warn { "SLA 2 is complete, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        if (sla2CompletedAt == null) {
                            log.warn { "SLA 2 is complete, but complete date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val diffDuration = java.time.Duration.between(sla2CompletedAt, sla2DueDate).toKotlinDuration()

                        SlaInfo(
                            state = SlaState.COMPLETE,
                            duration = diffDuration.toIsoString()
                        )
                    }

                    SlaState.PAUSE -> run {
                        if (sla2SpentTime == null) {
                            log.warn { "SLA 2 on pause, but spent time is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val priority = SdPriority.from(priority!!)
                        val slaDurations = slaParametersProvider.get(client, priority)

                        val sla2Duration = slaDurations.timeResolve
                        val spentDuration = sla2SpentTime.toDuration(DurationUnit.MILLISECONDS)

                        val remainingDuration = sla2Duration - spentDuration

                        SlaInfo(
                            state = SlaState.PAUSE,
                            duration = remainingDuration.toIsoString()
                        )
                    }

                    else -> {
                        log.warn { "SLA 2 unsupported state. $this" }
                        SlaInfo.UNDEFINED
                    }
                }
            }

            else -> {
                log.warn { "Unsupported SLA type $type" }
                SlaInfo.UNDEFINED
            }
        }
    }

    data class RequestInfo(
        val client: EntityRef = EntityRef.EMPTY,
        val priority: String? = null,

        @AttName("sla_1_state")
        val sla1State: SlaState? = null,

        @AttName("sla_2_state")
        val sla2State: SlaState? = null,

        @AttName("firstReactionTimestamp")
        val sla1CompletedAt: Instant? = null,

        @AttName("sla_1_due_date")
        val sla1DueDate: Instant? = null,

        @AttName("closedTimestamp")
        val sla2CompletedAt: Instant? = null,

        @AttName("sla_2_spent_time")
        val sla2SpentTime: Long? = null,

        @AttName("sla_2_due_date")
        val sla2DueDate: Instant? = null
    )

    private data class SlaRecord(
        val sla1Info: SlaInfo,
        val sla2Info: SlaInfo
    )

    data class SlaInfo(
        val state: SlaState?,
        // iso 8601 duration
        val duration: String
    ) {
        companion object {
            val UNDEFINED = SlaInfo(null, Duration.ZERO.toIsoString())
        }
    }
}

private data class SlaQuery(
    val record: EntityRef
)
