package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_ATT_SLA_1_COMPLETED_DURATION_MS
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_ATT_SLA_2_COMPLETED_DURATION_MS
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SdDueDateService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaParametersProvider
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaState
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaType
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager
import ru.citeck.ecos.webapp.servicedesk.domain.sla.converter.toSlaHmr
import ru.citeck.ecos.wkgsch.lib.schedule.WorkingSchedule
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration

@Component
class SdSlaParamsRecords(
    private val slaManager: SlaManager
) : AbstractRecordsDao(),
    RecordsQueryDao {
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
    private val slaParametersProvider: SlaParametersProvider,
    private val sdDueDateService: SdDueDateService
) : AbstractRecordsDao(),
    RecordsQueryDao {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun getId(): String {
        return "sd-sla"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val query = recsQuery.getQuery(SlaQuery::class.java)
        val refs = when {
            query.records.isNotEmpty() -> query.records
            query.record.isNotEmpty() -> listOf(query.record)
            else -> {
                log.warn { "Empty SD request not allowed" }
                return null
            }
        }

        val reqs = recordsService.getAtts(refs, RequestInfo::class.java)

        val schedulesByClient = HashMap<EntityRef, WorkingSchedule>()
        fun getWorkingTimeDiff(client: EntityRef, date0: Instant, date1: Instant): Duration {
            val schedule = schedulesByClient.getOrPut(client) {
                sdDueDateService.getWorkingScheduleForClient(client)
            }
            // todo: negative diff should be calculated in WorkingSchedule, but now it is not supported
            val duration = if (date0.isAfter(date1)) {
                schedule.getWorkingTime(date1, date0).negated()
            } else {
                schedule.getWorkingTime(date0, date1)
            }
            return duration.toKotlinDuration()
        }

        val records = ArrayList<SlaRecord?>(refs.size)
        refs.forEachIndexed { idx, ref ->
            val req = reqs[idx]
            if (req.client == EntityRef.EMPTY || req.priority == null) {
                records.add(null)
                return@forEachIndexed
            }
            val getDiff = { d0: Instant, d1: Instant -> getWorkingTimeDiff(req.client, d0, d1) }
            val sla1 = req.toSlaInfo(SlaType.SLA1, getDiff)
            val sla2 = req.toSlaInfo(SlaType.SLA2, getDiff)

            log.debug { "SLA info for $ref: \nsla1: $sla1 \nsla2:$sla2" }

            records.add(SlaRecord(recordRef = ref, sla1Info = sla1, sla2Info = sla2, req, getDiff))
        }

        val result = RecsQueryRes<SlaRecord>()
        result.setRecords(records)
        return result
    }

    private fun RequestInfo.toSlaInfo(type: SlaType, getDiff: (from: Instant, to: Instant) -> Duration): SlaInfo {
        return when (type) {
            SlaType.SLA1 -> {
                // Return static completed state
                if (sla2State == SlaState.COMPLETE && sla1CompletedDurationMs != null && sla1CompletedDurationMs > 0) {
                    return SlaInfo(
                        state = SlaState.COMPLETE,
                        sla1CompletedDurationMs.toDuration(DurationUnit.MILLISECONDS)
                    )
                }

                // Dynamic calculation, while SD request is in progress
                return when (sla1State) {
                    SlaState.CREATED -> SlaInfo(
                        state = SlaState.CREATED,
                        Duration.ZERO
                    )

                    SlaState.RUNNING -> run {
                        if (sla1DueDate == null) {
                            log.warn { "SLA 1 is running, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val now = Instant.now()
                        val diffDuration = getDiff(now, sla1DueDate)

                        SlaInfo(
                            state = SlaState.RUNNING,
                            diffDuration
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

                        val diffDuration = getDiff(sla1CompletedAt, sla1DueDate)

                        SlaInfo(
                            state = SlaState.COMPLETE,
                            diffDuration
                        )
                    }

                    else -> {
                        log.warn { "SLA 1 unsupported state. $this" }
                        SlaInfo.UNDEFINED
                    }
                }
            }

            SlaType.SLA2 -> {
                // Return static completed state
                if (sla2State == SlaState.COMPLETE && sla2CompletedDurationMs != null && sla2CompletedDurationMs > 0) {
                    return SlaInfo(
                        state = SlaState.COMPLETE,
                        sla2CompletedDurationMs.toDuration(DurationUnit.MILLISECONDS)
                    )
                }

                val remainingPausedDuration = fun (): Duration? {
                    if (sla2SpentTime == null || sla2SpentTime == 0L) {
                        return null
                    }

                    val priority = SdPriority.from(priority!!)
                    val slaDurations = slaParametersProvider.get(client, priority)

                    val sla2Duration = slaDurations.timeResolve
                    val spentDuration = sla2SpentTime.toDuration(DurationUnit.MILLISECONDS)

                    return sla2Duration - spentDuration
                }

                // Dynamic calculation, while SD request is in progress
                return when (sla2State) {
                    SlaState.CREATED -> SlaInfo(
                        state = SlaState.CREATED,
                        Duration.ZERO
                    )

                    SlaState.RUNNING -> run {
                        if (sla2DueDate == null) {
                            log.warn { "SLA 2 is running, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val now = Instant.now()
                        val diffDuration = getDiff(now, sla2DueDate)

                        SlaInfo(
                            state = SlaState.RUNNING,
                            diffDuration
                        )
                    }

                    SlaState.COMPLETE -> run {
                        // If SLA 2 was in a pause, we calculate duration from the last resume time
                        remainingPausedDuration()?.let {
                            return SlaInfo(
                                state = SlaState.COMPLETE,
                                it
                            )
                        }

                        if (sla2DueDate == null) {
                            log.warn { "SLA 2 is complete, but due date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        if (sla2CompletedAt == null) {
                            log.warn { "SLA 2 is complete, but complete date is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        val diffDuration = getDiff(sla2CompletedAt, sla2DueDate)

                        SlaInfo(
                            state = SlaState.COMPLETE,
                            diffDuration
                        )
                    }

                    SlaState.PAUSE -> run {
                        val remainingDuration = remainingPausedDuration() ?: let {
                            log.warn { "SLA 2 on pause, but spent time is null. $this" }
                            return@run SlaInfo.UNDEFINED
                        }

                        SlaInfo(
                            state = SlaState.PAUSE,
                            remainingDuration
                        )
                    }

                    else -> {
                        if (!status.isNullOrBlank()) {
                            log.warn { "SLA 2 unsupported state. $this" }
                        }

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

        @AttName(StatusConstants.ATT_STATUS_STR)
        val status: String? = null,

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

        @AttName("sla_2_last_resume_time!sla_2_start_time")
        val sla2LastResumeTime: Instant? = null,

        @AttName("sla_2_due_date")
        val sla2DueDate: Instant? = null,

        @AttName(SD_ATT_SLA_1_COMPLETED_DURATION_MS)
        val sla1CompletedDurationMs: Long? = null,

        @AttName(SD_ATT_SLA_2_COMPLETED_DURATION_MS)
        val sla2CompletedDurationMs: Long? = null

    )

    private data class SlaRecord(
        val recordRef: EntityRef,
        val sla1Info: SlaInfo,
        val sla2Info: SlaInfo,
        val requestInfo: RequestInfo,
        val getDiff: (date0: Instant, date1: Instant) -> Duration
    ) {
        fun getSla2CurrentSpentTimeMs(): Long {
            val lastResumeTime = requestInfo.sla2LastResumeTime ?: Instant.now()
            val diff = getDiff(lastResumeTime, Instant.now())
            return (requestInfo.sla2SpentTime ?: 0L) + diff.toLong(DurationUnit.MILLISECONDS)
        }
    }

    data class SlaInfo(
        val state: SlaState?,
        val durationInstance: Duration,
    ) {
        companion object {
            val UNDEFINED = SlaInfo(null, Duration.ZERO)
        }

        // iso 8601 duration
        val duration: String = durationInstance.toIsoString()

        val durationHumanReadable: String = durationInstance.toSlaHmr()

        val durationMs: Long = durationInstance.toLong(DurationUnit.MILLISECONDS)
    }
}

private data class SlaQuery(
    val record: EntityRef = EntityRef.EMPTY,
    val records: List<EntityRef> = emptyList()
)
