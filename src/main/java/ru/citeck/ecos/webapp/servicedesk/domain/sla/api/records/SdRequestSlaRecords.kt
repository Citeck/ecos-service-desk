package ru.citeck.ecos.webapp.servicedesk.domain.sla.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaDueDates
import ru.citeck.ecos.webapp.servicedesk.domain.sla.api.SlaManager

@Component
class SdRequestSlaRecords(
    private val slaManager: SlaManager
) : AbstractRecordsDao(), RecordsQueryDao {
    override fun getId(): String {
        return "sd-request-sla"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val record = recsQuery.getQuery(SlasQuery::class.java).record

        val dueDate = slaManager.getDueDates(record)

        val result = RecsQueryRes<SlaDueDates>()
        result.setRecords(listOf(dueDate))

        return result

    }

    private data class SlasQuery(
        val record: EntityRef
    )
}


