package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.impl.mem.InMemDataRecordsDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import ru.citeck.ecos.webapp.servicedesk.ServiceDeskApp

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ServiceDeskApp::class])
class SdClientsMappingRefCacheTest {

    companion object {
        private const val MAPPING_SRC_ID = "emodel/clients-mapping-type"
        private const val MAPPING_TYPE_REF = "emodel/type@clients-mapping-type"
        private const val CLIENTS_SRC_ID = "emodel/clients-type"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var cache: SdClientsMappingRefCache

    @BeforeEach
    fun setUp() {
        cache.invalidateAll()
        recordsService.register(InMemDataRecordsDao(MAPPING_SRC_ID))
        recordsService.register(InMemDataRecordsDao(CLIENTS_SRC_ID))
    }

    private fun createMapping(localId: String, client: EntityRef?): EntityRef {
        val atts = ObjectData.create()
            .set("id", localId)
            .set("_type", MAPPING_TYPE_REF)
        if (client != null) {
            atts["client"] = client.toString()
        }
        return recordsService.create(MAPPING_SRC_ID, atts)
    }

    @Test
    fun `returns specific mapping for client`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-1")
        val mappingRef = createMapping("mapping-1", client)

        val resolved = cache.getMappingRef(client)

        assertThat(resolved).isEqualTo(mappingRef)
    }

    @Test
    fun `falls back to default mapping when no specific exists`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-2")
        val defaultRef = createMapping("default-mapping", client = null)

        val resolved = cache.getMappingRef(client)

        assertThat(resolved).isEqualTo(defaultRef)
    }

    @Test
    fun `empty client returns default mapping`() {
        val defaultRef = createMapping("default-mapping", client = null)

        val resolved = cache.getMappingRef(EntityRef.EMPTY)

        assertThat(resolved).isEqualTo(defaultRef)
    }

    @Test
    fun `returns EMPTY when no mappings match and caches the negative result`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-3")

        val first = cache.getMappingRef(client)
        val second = cache.getMappingRef(client)

        assertThat(first).isEqualTo(EntityRef.EMPTY)
        assertThat(second).isEqualTo(EntityRef.EMPTY)
    }

    @Test
    fun `invalidate forces reresolve on next call`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-4")
        // No mapping exists yet → negative cache
        cache.getMappingRef(client)

        // Now create a mapping AFTER the negative cache entry was stored
        val mappingRef = createMapping("mapping-4", client)
        // Without invalidation, cache still returns EMPTY
        assertThat(cache.getMappingRef(client)).isEqualTo(EntityRef.EMPTY)

        cache.invalidate(client.toString())

        assertThat(cache.getMappingRef(client)).isEqualTo(mappingRef)
    }

    @Test
    fun `invalidateAll clears specific and default cache keys`() {
        val clientA = EntityRef.valueOf("$CLIENTS_SRC_ID@client-A")
        val clientB = EntityRef.valueOf("$CLIENTS_SRC_ID@client-B")
        val mappingA = createMapping("mapping-A", clientA)
        val mappingB = createMapping("mapping-B", clientB)

        // Prime the cache
        assertThat(cache.getMappingRef(clientA)).isEqualTo(mappingA)
        assertThat(cache.getMappingRef(clientB)).isEqualTo(mappingB)

        // Delete one mapping, then invalidateAll → that key must re-resolve to EMPTY
        recordsService.delete(mappingA)
        cache.invalidateAll()

        assertThat(cache.getMappingRef(clientA)).isEqualTo(EntityRef.EMPTY)
        assertThat(cache.getMappingRef(clientB)).isEqualTo(mappingB)
    }
}
