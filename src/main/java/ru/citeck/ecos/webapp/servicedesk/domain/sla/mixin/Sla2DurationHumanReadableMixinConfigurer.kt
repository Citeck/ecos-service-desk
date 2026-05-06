package ru.citeck.ecos.webapp.servicedesk.domain.sla.mixin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.external.ExtAttMixinConfigurer
import ru.citeck.ecos.records3.record.mixin.external.ExtMixinConfig
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

@Configuration
class SlaDurationHumanReadableMixinConfiguration {

    companion object {
        private const val SD_REQUEST_TYPE = "sd-request-type"

        private const val ATT_SLA_1_DURATION_HUMAN_READABLE = "sla1DurationHmr"
        private const val ATT_SLA_2_DURATION_HUMAN_READABLE = "sla2DurationHmr"
    }

    @Bean
    fun slaDurationHumanReadableMixin(recordsService: RecordsService): ExtAttMixinConfigurer {
        return object : ExtAttMixinConfigurer {
            override fun configure(settings: ExtMixinConfig) {
                settings.setEcosType(SD_REQUEST_TYPE)
                    .addProvidedAtts(
                        ATT_SLA_1_DURATION_HUMAN_READABLE,
                        ATT_SLA_2_DURATION_HUMAN_READABLE
                    )
                    .addRequiredAtts(mapOf("recordRef" to "?id"))
                    .withRawHandler { ctx, reqAtts, schemaAtt ->
                        val ref = reqAtts["recordRef"].toString().toEntityRef()
                        val batch = ctx.computeIfAbsent("sd-sla-batch") {
                            SlaBatch(recordsService)
                        }
                        batch.request(ref, schemaAtt.name)
                    }
            }
        }
    }

    private class SlaBatch(
        private val recordsService: RecordsService
    ) {

        private val flushed = AtomicBoolean(false)
        private val refs = LinkedHashSet<EntityRef>()
        private val future = CompletableFuture<Map<EntityRef, SlaData>>()
        private val promise: Promise<Map<EntityRef, SlaData>> = Promises.create(future) { flush() }

        fun request(ref: EntityRef, attName: String): Promise<Any?> {
            refs.add(ref)
            return promise.then { resultByRef ->
                val data = resultByRef[ref] ?: return@then null
                when (attName) {
                    ATT_SLA_1_DURATION_HUMAN_READABLE -> data.sla1DurationHumanReadable
                    ATT_SLA_2_DURATION_HUMAN_READABLE -> data.sla2DurationHumanReadable
                    else -> null
                }
            }
        }

        private fun flush() {
            if (!flushed.compareAndSet(false, true)) {
                return
            }
            try {
                if (refs.isEmpty()) {
                    future.complete(emptyMap())
                    return
                }
                val resByRef = AuthContext.runAsSystem {
                    val res = recordsService.query(
                        RecordsQuery.create {
                            withSourceId("sd-sla")
                            withQuery(mapOf("records" to refs.toList()))
                        },
                        SlaData::class.java
                    )
                    res.getRecords().associateBy { it.recordRef }
                }
                future.complete(resByRef)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
    }

    private data class SlaData(
        @AttName("recordRef")
        val recordRef: EntityRef,

        @AttName("sla1Info.durationHumanReadable")
        val sla1DurationHumanReadable: String,

        @AttName("sla2Info.durationHumanReadable")
        val sla2DurationHumanReadable: String
    )
}
