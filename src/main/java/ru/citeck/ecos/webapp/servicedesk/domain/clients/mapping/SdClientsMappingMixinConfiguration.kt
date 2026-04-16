package ru.citeck.ecos.webapp.servicedesk.domain.clients.mapping

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.records3.record.mixin.external.ExtAttMixinConfigurer
import ru.citeck.ecos.records3.record.mixin.external.ExtMixinConfig
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Configuration
class SdClientsMappingMixinConfiguration {

    companion object {
        private const val SD_REQUEST_TYPE = "sd-request-type"
        private const val ATT_MAPPING_CLIENT_REF = "mappingClientRef"
        private const val REQUIRED_ATT_CLIENT = "client"
    }

    @Bean
    fun sdClientsMappingRefMixin(
        cache: SdClientsMappingRefCache
    ): ExtAttMixinConfigurer {
        return object : ExtAttMixinConfigurer {

            override fun configure(settings: ExtMixinConfig) {
                settings.setEcosType(SD_REQUEST_TYPE)
                    .addProvidedAtt(ATT_MAPPING_CLIENT_REF)
                    .addRequiredAtts(mapOf(REQUIRED_ATT_CLIENT to "client?id"))
                    .withHandler { data ->
                        val client = data[REQUIRED_ATT_CLIENT].asText().toEntityRef()
                        cache.getMappingRef(client)
                    }
            }
        }
    }
}
