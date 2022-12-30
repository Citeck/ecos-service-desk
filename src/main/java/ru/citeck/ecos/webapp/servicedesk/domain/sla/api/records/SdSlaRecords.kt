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
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager
import java.time.Instant
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

        private const val UNDEFINED_STATUS = "неизвестный статус"
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

        val sla1Info: String = when (req.sla1State) {
            SlaState.CREATED -> "еще не запущен"
            SlaState.RUNNING -> run {
                if (req.sla1DueDate == null) {
                    log.warn { "SLA 1 is running, but due date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                val now = Instant.now()
                val isOverdue = now.isAfter(req.sla1DueDate)

                val diffDuration = java.time.Duration.between(now, req.sla1DueDate).toKotlinDuration()
                var result = "запущен, "
                result += if (isOverdue) {
                    "просрочен на $diffDuration"
                } else {
                    "осталось $diffDuration"
                }

                result
            }

            SlaState.COMPLETE -> run {
                if (req.sla1DueDate == null) {
                    log.warn { "SLA 1 is complete, but due date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                if (req.sla1CompletedAt == null) {
                    log.warn { "SLA 1 is complete, but complete date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                val isOverdue = req.sla1CompletedAt.isAfter(req.sla1DueDate)
                val diffDuration = java.time.Duration.between(req.sla1CompletedAt, req.sla1DueDate).toKotlinDuration()
                var result = "завершен, "
                result += if (isOverdue) {
                    "просрочен на $diffDuration"
                } else {
                    "осталось $diffDuration"
                }

                result
            }

            else -> {
                log.warn { "SLA 1 unsupported state. Query=$recsQuery, Info=$req" }
                UNDEFINED_STATUS
            }
        }

        val sla2Info = when (req.sla2State) {
            SlaState.CREATED -> "еще не запущен"
            SlaState.RUNNING -> run {
                if (req.sla2DueDate == null) {
                    log.warn { "SLA 2 is running, but due date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                val now = Instant.now()
                val isOverdue = now.isAfter(req.sla2DueDate)

                val diffDuration = java.time.Duration.between(now, req.sla2DueDate).toKotlinDuration()
                var result = "запущен, "
                result += if (isOverdue) {
                    "просрочен на $diffDuration"
                } else {
                    "осталось $diffDuration"
                }

                result
            }

            SlaState.COMPLETE -> run {
                if (req.sla2DueDate == null) {
                    log.warn { "SLA 2 is complete, but due date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                if (req.sla2CompletedAt == null) {
                    log.warn { "SLA 2 is complete, but complete date is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                val isOverdue = req.sla2CompletedAt.isAfter(req.sla2DueDate)
                val diffDuration = java.time.Duration.between(req.sla2CompletedAt, req.sla2DueDate).toKotlinDuration()
                var result = "завершен, "
                result += if (isOverdue) {
                    "просрочен на $diffDuration"
                } else {
                    "осталось $diffDuration"
                }

                result
            }

            SlaState.PAUSE -> run {
                if (req.sla2SpentTime == null) {
                    log.warn { "SLA 2 on pause, but spent time is null. Query=$recsQuery, Info=$req" }
                    return@run UNDEFINED_STATUS
                }

                val priority = SdPriority.from(req.priority)
                val slaDurations = slaParametersProvider.get(req.client, priority)

                val sla2Duration = slaDurations.timeResolve
                val spentDuration = req.sla2SpentTime.toDuration(DurationUnit.MILLISECONDS)

                val remainingDuration = sla2Duration - spentDuration
                val isOverdue = remainingDuration.isNegative()

                var result = "пауза, "
                result += if (isOverdue) {
                    "просрочен на $remainingDuration"
                } else {
                    "осталось $remainingDuration"
                }

                result
            }

            else -> {
                log.warn { "SLA 2 unsupported state. Query=$recsQuery, Info=$req" }
                UNDEFINED_STATUS
            }
        }

        val result = RecsQueryRes<SlaInfo>()
        result.setRecords(listOf(SlaInfo(sla1Info, sla2Info)))

        return result
    }

    private data class RequestInfo(
        val client: EntityRef,
        val priority: String,

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

    private data class SlaInfo(
        val sla1Info: String,
        val sla2Info: String
    )
}

private data class SlaQuery(
    val record: EntityRef
)


