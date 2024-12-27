package ru.citeck.ecos.webapp.servicedesk.domain.sla.mixin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.external.ExtAttMixinConfigurer
import ru.citeck.ecos.records3.record.mixin.external.ExtMixinConfig
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef


@Configuration
class SlaDurationHumanReadableMixinConfiguration {

    companion object {
        private const val SD_REQUEST_TYPE = "sd-request-type"

        private const val ATT_SLA_1_DURATION_HUMAN_READABLE = "sla1DurationHmr"
        private const val ATT_SLA_2_DURATION_HUMAN_READABLE = "sla2DurationHmr"
    }

    private fun getSlaData(recordRef: EntityRef, recordsService: RecordsService): SlaData? {
        val sla = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId("service-desk/sd-sla")
                withQuery(
                    mapOf("record" to recordRef)
                )
            },
            SlaData::class.java
        )

        return sla
    }

    @Bean
    fun sla1DurationHumanReadableMixin(recordsService: RecordsService): ExtAttMixinConfigurer {
        return object : ExtAttMixinConfigurer {

            override fun configure(settings: ExtMixinConfig) {
                settings.setEcosType(SD_REQUEST_TYPE)
                    .addProvidedAtt(ATT_SLA_1_DURATION_HUMAN_READABLE)
                    .addRequiredAtts(mapOf("recordRef" to "?id"))
                    .withHandler { data ->
                        getSlaData(data["recordRef"].toEntityRef(), recordsService)?.sla1DurationHumanReadable
                    }
            }


        }
    }

    @Bean
    fun sla2DurationHumanReadableMixin(recordsService: RecordsService): ExtAttMixinConfigurer {
        return object : ExtAttMixinConfigurer {

            override fun configure(settings: ExtMixinConfig) {
                settings.setEcosType(SD_REQUEST_TYPE)
                    .addProvidedAtt(ATT_SLA_2_DURATION_HUMAN_READABLE)
                    .addRequiredAtts(mapOf("recordRef" to "?id"))
                    .withHandler { data ->
                        getSlaData(data["recordRef"].toEntityRef(), recordsService)?.sla2DurationHumanReadable
                    }
            }


        }
    }

    private data class SlaData(
        @AttName("sla1Info.durationHumanReadable")
        val sla1DurationHumanReadable: String,

        @AttName("sla2Info.durationHumanReadable")
        val sla2DurationHumanReadable: String
    )
}

