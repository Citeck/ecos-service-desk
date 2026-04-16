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
class SdClientsMappingMixinIntegrationTest {

    companion object {
        private const val MAPPING_SRC_ID = "emodel/clients-mapping-type"
        private const val MAPPING_TYPE_REF = "emodel/type@clients-mapping-type"
        private const val CLIENTS_SRC_ID = "emodel/clients-type"
        private const val SD_REQUEST_SRC_ID = "emodel/sd-request"
        private const val SD_REQUEST_TYPE_REF = "emodel/type@sd-request-type"
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
        recordsService.register(InMemDataRecordsDao(SD_REQUEST_SRC_ID))
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

    private fun createSdRequest(localId: String, client: EntityRef?): EntityRef {
        val atts = ObjectData.create()
            .set("id", localId)
            .set("_type", SD_REQUEST_TYPE_REF)
        if (client != null) {
            atts["client"] = client.toString()
        }
        return recordsService.create(SD_REQUEST_SRC_ID, atts)
    }

    private fun resolveMappingRef(sdRequestRef: EntityRef): String {
        return recordsService.getAtt(sdRequestRef, "mappingClientRef?id").asText()
    }

    @Test
    fun `returns specific mapping ref via mixin`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-Z")
        val mappingRef = createMapping("mapping-Z", client)
        val sdRequestRef = createSdRequest("SD-42", client)

        assertThat(EntityRef.valueOf(resolveMappingRef(sdRequestRef))).isEqualTo(mappingRef)
    }

    @Test
    fun `falls back to default mapping when no specific mapping exists`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-no-specific")
        val defaultRef = createMapping("default-mapping", client = null)
        val sdRequestRef = createSdRequest("SD-43", client)

        assertThat(EntityRef.valueOf(resolveMappingRef(sdRequestRef))).isEqualTo(defaultRef)
    }

    @Test
    fun `returns empty when no mapping exists at all`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-no-map")
        val sdRequestRef = createSdRequest("SD-44", client)

        assertThat(resolveMappingRef(sdRequestRef)).isEmpty()
    }

    @Test
    fun `returns default mapping when sd-request has no client`() {
        val defaultRef = createMapping("default-mapping", client = null)
        val sdRequestRef = createSdRequest("SD-45", client = null)

        assertThat(EntityRef.valueOf(resolveMappingRef(sdRequestRef))).isEqualTo(defaultRef)
    }

    @Test
    fun `returns same ref on repeated lookup without refetch`() {
        val client = EntityRef.valueOf("$CLIENTS_SRC_ID@client-primed")
        val mappingRef = createMapping("mapping-primed", client)
        val sdRequestRef = createSdRequest("SD-46", client)

        val first = resolveMappingRef(sdRequestRef)
        // Delete the underlying mapping; if the mixin went back to the store, the next
        // call would return empty. It must return the cached ref instead.
        recordsService.delete(mappingRef)
        val second = resolveMappingRef(sdRequestRef)

        assertThat(EntityRef.valueOf(first)).isEqualTo(mappingRef)
        assertThat(first).isEqualTo(second)
    }
}
