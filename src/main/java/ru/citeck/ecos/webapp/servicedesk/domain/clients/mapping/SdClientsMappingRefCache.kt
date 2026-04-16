package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.Consistency
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration

@Component
class SdClientsMappingRefCache(
    private val recordsService: RecordsService
) {

    companion object {
        private const val MAPPING_TYPE_ID = "clients-mapping-type"
        private const val MAPPING_SRC_ID = "emodel/clients-mapping-type"
        private const val ATT_CLIENT = "client"
        private const val ATT_CREATED = "_created"
        private const val EMPTY_CLIENT_KEY = ""
    }

    private val cache: Cache<String, EntityRef> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(1000)
        .build()

    fun getMappingRef(client: EntityRef): EntityRef {
        val key = if (EntityRef.isEmpty(client)) {
            EMPTY_CLIENT_KEY
        } else {
            client.toString()
        }
        return cache.get(key) {
            AuthContext.runAsSystem {
                resolve(client)
            }
        }
    }

    fun invalidate(clientKey: String) {
        cache.invalidate(clientKey)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    private fun resolve(client: EntityRef): EntityRef {
        if (EntityRef.isEmpty(client)) {
            return queryDefaultMapping()
        }
        val specific = queryMapping(Predicates.eq(ATT_CLIENT, client))
        if (EntityRef.isNotEmpty(specific)) {
            return specific
        }
        return queryDefaultMapping()
    }

    private fun queryDefaultMapping(): EntityRef {
        return queryMapping(Predicates.empty(ATT_CLIENT))
    }

    private fun queryMapping(clientPredicate: Predicate): EntityRef {
        val query = RecordsQuery.create {
            withEcosType(MAPPING_TYPE_ID)
            withSourceId(MAPPING_SRC_ID)
            withConsistency(Consistency.EVENTUAL)
            withQuery(clientPredicate)
            withSortBy(SortBy(ATT_CREATED, false))
            withMaxItems(1)
        }
        return recordsService.queryOne(query) ?: EntityRef.EMPTY
    }
}
