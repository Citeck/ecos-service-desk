package ru.citeck.ecos.webapp.servicedesk.domain.sla

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.wkgsch.lib.schedule.WorkingScheduleService
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Component
class SdDueDateService(
    private val recordsService: RecordsService,
    private val workingScheduleService: WorkingScheduleService
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val CLIENTS_SCHEDULE_MAPPING_ID = "clients-mapping-type"
        private const val SCHEDULE_ATT = "workingSchedule?localId"
        private const val DEFAULT_SCHEDULE_ID = "DEFAULT"
    }

    fun getDueDate(duration: Duration, record: EntityRef): Instant {
        val now = Instant.now()
        val client = recordsService.getAtts(record, SdRequestAtts::class.java).client

        val scheduleMappingId = recordsService.queryOne(
            RecordsQuery.create()
                .withSourceId("emodel/$CLIENTS_SCHEDULE_MAPPING_ID")
                .withQuery(
                    Predicates.and(
                        Predicates.eq("_type", "emodel/type@$CLIENTS_SCHEDULE_MAPPING_ID"),
                        Predicates.eq("client", client)
                    )
                )
                .build()
        ).toEntityRef()

        val scheduleId = getScheduleIdOrDefault(scheduleMappingId)

        val result = if (duration.isPositive()) {
            workingScheduleService.getScheduleById(scheduleId)
                .addWorkingTime(now, duration.toJavaDuration())
        } else {
            now.plus(duration.toJavaDuration())
        }

        log.debug { "Due date for from=$now, duration=$duration, result=$result" }

        return result
    }

    private fun getScheduleIdOrDefault(scheduleMapping: EntityRef): String {
        val result = recordsService.getAtt(scheduleMapping, SCHEDULE_ATT).asText()
        return result.ifBlank {
            DEFAULT_SCHEDULE_ID
        }
    }

    private data class SdRequestAtts(
        val client: EntityRef
    )
}
