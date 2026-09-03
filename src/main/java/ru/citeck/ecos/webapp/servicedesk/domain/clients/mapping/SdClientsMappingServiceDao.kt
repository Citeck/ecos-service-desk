package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class SdClientsMappingServiceDao(
    private val cache: SdClientsMappingRefCache
) : RecordsQueryDao, AbstractRecordsDao() {

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        if (recsQuery.language == MappingByClientQuery.LANG) {
            val query = recsQuery.getQuery(MappingByClientQuery::class.java)
            return MappingByClientQuery.Resp(cache.getMappingRef(query.client ?: EntityRef.EMPTY))
        }
        return null
    }

    override fun getId(): String {
        return "clients-mapping-service"
    }

    data class MappingByClientQuery(
        val client: EntityRef?
    ) {
        companion object {
            const val LANG = "mapping-by-client"
        }

        data class Resp(
            val mappingRef: EntityRef
        )
    }
}
