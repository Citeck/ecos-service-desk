package ru.citeck.ecos.webapp.servicedesk.domain.sla.mixin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.external.ExtAttMixinConfigurer
import ru.citeck.ecos.records3.record.mixin.external.ExtMixinConfig
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.time.Duration


@Component
class Sla2DurationMixinConfigurer(
    private val recordsService: RecordsService
) : ExtAttMixinConfigurer {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val SD_REQUEST_TYPE = "sd-request-type"
        private const val ATT_SLA_2_DURATION = "sla2Duration"
    }

    override fun configure(settings: ExtMixinConfig) {
        settings.setEcosType(SD_REQUEST_TYPE)
            .addProvidedAtt(ATT_SLA_2_DURATION)
            .addRequiredAtts(mapOf("recordRef" to "?id"))
            .withHandler { data ->
                handler(data["recordRef"].toEntityRef())
            }
    }

    private fun handler(recordRef: EntityRef): String {
        val sla = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId("service-desk/sd-sla")
                withQuery(
                    mapOf("record" to recordRef)
                )
            },
            SlaData::class.java
        )

        if (sla?.sla2Duration != null) {
            val duration = Duration.parse(sla.sla2Duration)
            val hours = duration.toHoursPart()
            var minutes = duration.toMinutesPart()

            var result = ""
            if (hours == 0 && minutes < 0) {
                result += "-"
            }
            if (minutes < 0) {
                minutes = -minutes
            }
            result += "${hours}H ${minutes}M"
            log.debug { "SLA2 info SD=$recordRef result=$result" }
            return result
        }
        return ""
    }

    private data class SlaData(
        @AttName("sla2Info.duration")
        val sla2Duration: String?
    )
}
