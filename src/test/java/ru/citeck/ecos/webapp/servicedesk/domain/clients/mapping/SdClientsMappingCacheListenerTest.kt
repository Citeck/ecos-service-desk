package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.impl.mem.InMemDataRecordsDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import ru.citeck.ecos.webapp.servicedesk.ServiceDeskApp

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ServiceDeskApp::class])
class SdClientsMappingCacheListenerTest {

    companion object {
        private const val MAPPING_SRC_ID = "emodel/clients-mapping-type"
        private const val MAPPING_TYPE_ID = "clients-mapping-type"
        private const val MAPPING_TYPE_REF = "emodel/type@clients-mapping-type"
        private const val CLIENTS_SRC_ID = "emodel/clients-type"
    }

    @Autowired
    private lateinit var recordsService: RecordsService
    @Autowired
    private lateinit var cache: SdClientsMappingRefCache
    @Autowired
    private lateinit var eventsService: EventsService

    private val createdEmitter by lazy {
        eventsService.getEmitter(
            EmitterConfig.create<RecordCreatedEvent> {
                eventClass = RecordCreatedEvent::class.java
                eventType = RecordCreatedEvent.TYPE
                source = "test"
            }
        )
    }
    private val changedEmitter by lazy {
        eventsService.getEmitter(
            EmitterConfig.create<RecordChangedEvent> {
                eventClass = RecordChangedEvent::class.java
                eventType = RecordChangedEvent.TYPE
                source = "test"
            }
        )
    }
    private val deletedEmitter by lazy {
        eventsService.getEmitter(
            EmitterConfig.create<RecordDeletedEvent> {
                eventClass = RecordDeletedEvent::class.java
                eventType = RecordDeletedEvent.TYPE
                source = "test"
            }
        )
    }

    @BeforeEach
    fun setUp() {
        cache.invalidateAll()
        recordsService.register(InMemDataRecordsDao(MAPPING_SRC_ID))
        recordsService.register(InMemDataRecordsDao(CLIENTS_SRC_ID))
    }

    private fun createMapping(localId: String, client: EntityRef?, created: String? = null): EntityRef {
        val atts = ObjectData.create()
            .set("id", localId)
            .set("_type", MAPPING_TYPE_REF)
        if (client != null) {
            atts["client"] = client.toString()
        }
        if (created != null) {
            atts["_created"] = created
        }
        return recordsService.create(MAPPING_SRC_ID, atts)
    }

    private fun typeInfo(): TypeInfo = TypeInfo.create {
        withId(MAPPING_TYPE_ID)
    }

    @Test
    fun `record-created for specific mapping invalidates only that client key`() {
        val clientX = EntityRef.valueOf("$CLIENTS_SRC_ID@client-X")
        val clientY = EntityRef.valueOf("$CLIENTS_SRC_ID@client-Y")
        val mappingX = createMapping("mapping-X", clientX, created = "2026-01-01T00:00:00Z")
        val mappingY = createMapping("mapping-Y", clientY, created = "2026-01-01T00:00:00Z")

        // Prime cache
        assertThat(cache.getMappingRef(clientX)).isEqualTo(mappingX)
        assertThat(cache.getMappingRef(clientY)).isEqualTo(mappingY)

        // Create a new specific mapping for clientX (superseding mappingX by _created order)
        val newMappingX = createMapping("mapping-X-new", clientX, created = "2026-02-01T00:00:00Z")

        createdEmitter.emit(RecordCreatedEvent(newMappingX, typeInfo(), false, emptyList()))

        // clientX key re-resolves (and picks up the newest); clientY key stays cached
        assertThat(cache.getMappingRef(clientX)).isEqualTo(newMappingX)
        assertThat(cache.getMappingRef(clientY)).isEqualTo(mappingY)
    }

    @Test
    fun `record-created for default mapping invalidates all keys`() {
        val clientA = EntityRef.valueOf("$CLIENTS_SRC_ID@client-A")
        val clientB = EntityRef.valueOf("$CLIENTS_SRC_ID@client-B")
        val mappingA = createMapping("mapping-A", clientA)
        val mappingB = createMapping("mapping-B", clientB)

        assertThat(cache.getMappingRef(clientA)).isEqualTo(mappingA)
        assertThat(cache.getMappingRef(clientB)).isEqualTo(mappingB)

        val defaultRef = createMapping("default-mapping", client = null)
        createdEmitter.emit(RecordCreatedEvent(defaultRef, typeInfo(), false, emptyList()))

        // Both keys re-resolved — they still point to their specific mappings, but cache was cleared
        assertThat(cache.getMappingRef(clientA)).isEqualTo(mappingA)
        assertThat(cache.getMappingRef(clientB)).isEqualTo(mappingB)
        // And a lookup for an unknown client now returns the default (would have been EMPTY before)
        val unknown = EntityRef.valueOf("$CLIENTS_SRC_ID@client-unknown")
        assertThat(cache.getMappingRef(unknown)).isEqualTo(defaultRef)
    }

    @Test
    fun `record-changed with client diff invalidates both before and after keys`() {
        val clientX = EntityRef.valueOf("$CLIENTS_SRC_ID@client-X")
        val clientY = EntityRef.valueOf("$CLIENTS_SRC_ID@client-Y")
        val mappingX = createMapping("mapping-X", clientX)

        // Prime cache for clientX (populated) and clientY (negative)
        assertThat(cache.getMappingRef(clientX)).isEqualTo(mappingX)
        assertThat(cache.getMappingRef(clientY)).isEqualTo(EntityRef.EMPTY)

        // Reassign mappingX's client from X to Y
        recordsService.mutate(
            mappingX,
            mapOf<String, Any?>("client" to clientY.toString())
        )
        changedEmitter.emit(
            RecordChangedEvent(
                record = mappingX,
                typeDef = typeInfo(),
                before = mapOf("client" to clientX.toString()),
                after = mapOf("client" to clientY.toString()),
                assocs = emptyList(),
                isDraft = false
            )
        )

        // clientX lookup — no more specific, no default → EMPTY
        assertThat(cache.getMappingRef(clientX)).isEqualTo(EntityRef.EMPTY)
        // clientY lookup — now resolves to mappingX
        assertThat(cache.getMappingRef(clientY)).isEqualTo(mappingX)
    }

    @Test
    fun `record-deleted for default mapping invalidates all keys`() {
        val clientA = EntityRef.valueOf("$CLIENTS_SRC_ID@client-A")
        val defaultRef = createMapping("default-mapping", client = null)

        // Prime cache — clientA has no specific, so resolves to default
        assertThat(cache.getMappingRef(clientA)).isEqualTo(defaultRef)

        recordsService.delete(defaultRef)
        deletedEmitter.emit(RecordDeletedEvent(defaultRef, typeInfo()))

        // After invalidation, clientA lookup → EMPTY (no more default)
        assertThat(cache.getMappingRef(clientA)).isEqualTo(EntityRef.EMPTY)
    }

    @Test
    fun `record-changed without client diff does not invalidate cache`() {
        val clientA = EntityRef.valueOf("$CLIENTS_SRC_ID@client-A")
        val mappingA = createMapping("mapping-A", clientA)

        // Prime cache
        assertThat(cache.getMappingRef(clientA)).isEqualTo(mappingA)

        // Emit a RecordChangedEvent whose diff does NOT touch the client attribute
        changedEmitter.emit(
            RecordChangedEvent(
                record = mappingA,
                typeDef = typeInfo(),
                before = mapOf("implFirstLineSupport" to "g1"),
                after = mapOf("implFirstLineSupport" to "g2"),
                assocs = emptyList(),
                isDraft = false
            )
        )

        // Delete the underlying record; the cache should still hold the stale ref
        // because the listener's filter (diff._has.client?bool == true) rejected
        // the event above, so no invalidation happened.
        recordsService.delete(mappingA)
        assertThat(cache.getMappingRef(clientA)).isEqualTo(mappingA)
    }
}
