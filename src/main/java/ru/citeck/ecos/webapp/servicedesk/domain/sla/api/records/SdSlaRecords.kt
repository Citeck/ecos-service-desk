package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SdDueDateService
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaParametersProvider
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaState
import ru.citeck.ecos.webapp.servicedesk.domain.sla.SlaType
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager
import ru.citeck.ecos.wkgsch.lib.schedule.WorkingSchedule
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
    private val slaParametersProvider: SlaParametersProvider,
    private val sdDueDateService: SdDueDateService
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

        var workingSchedule: WorkingSchedule? = null
        fun getWorkingTimeDiff(date0: Instant, date1: Instant): Duration {
            val schedule = workingSchedule ?: run {
                val loadedSchedule = sdDueDateService.getWorkingScheduleForClient(req.client)
                workingSchedule = loadedSchedule
                loadedSchedule
            }
            // todo: negative diff should be calculated in WorkingSchedule, but now it is not supported
            val duration = if (date0.isAfter(date1)) {
                schedule.getWorkingTime(date1, date0).negated()
            } else {
                schedule.getWorkingTime(date0, date1)
            }
            return duration.toKotlinDuration()
        }

        val sla1 = req.toSlaInfo(SlaType.SLA1) { d0, d1 -> getWorkingTimeDiff(d0, d1) }
        val sla2 = req.toSlaInfo(SlaType.SLA2) { d0, d1 -> getWorkingTimeDiff(d0, d1) }

        log.debug { "SLA info for $record: \nsla1: $sla1 \nsla2:$sla2" }

        val result = RecsQueryRes<SlaRecord>()
        result.setRecords(
            listOf(
                SlaRecord(
                    sla1Info = sla1,
                    sla2Info = sla2,
                    req
                ) { d0, d1 -> getWorkingTimeDiff(d0, d1) }
            )
        )
        return result
    }

    private fun RequestInfo.toSlaInfo(type: SlaType, getDiff: (date0: Instant, date1: Instant) -> Duration): SlaInfo {
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
                        val diffDuration = getDiff(now, sla1DueDate)

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

                        val diffDuration = getDiff(sla1CompletedAt, sla1DueDate)

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
                        val diffDuration = getDiff(now, sla2DueDate)

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

                        val diffDuration = getDiff(sla2CompletedAt, sla2DueDate)

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

        @AttName("sla_2_last_resume_time!sla_2_start_time")
        val sla2LastResumeTime: Instant? = null,

        @AttName("sla_2_due_date")
        val sla2DueDate: Instant? = null
    )

    private data class SlaRecord(
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
