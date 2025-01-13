package ru.citeck.ecos.webapp.servicedesk.domain.sla.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.PatchExecutionState
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOnApps
import ru.citeck.ecos.webapp.lib.patch.executor.bean.StatefulEcosPatch
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_ATT_SLA_1_COMPLETED_DURATION_MS
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_ATT_SLA_2_COMPLETED_DURATION_MS
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_SOURCE_ID
import ru.citeck.ecos.webapp.servicedesk.domain.request.SD_STATUS_CLOSED

@Component
@EcosPatch("fill-static-completed-sla-for-closed-sd-patch-4", "2025-01-10T00:00:00Z")
@EcosPatchDependsOnApps(AppName.EMODEL)
class FillStaticCompletedSlaForClosedSdPatch(
    private val recordsService: RecordsService
) : StatefulEcosPatch<FillStaticCompletedSlaForClosedSdPatch.PatchState> {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val BATCH_SIZE = 20
    }

    override fun execute(state: PatchState): PatchExecutionState<PatchState> {
        val foundSdRequests = recordsService.query(
            RecordsQuery.create {
                withSourceId(SD_SOURCE_ID)
                withQuery(
                    Predicates.eq(StatusConstants.ATT_STATUS, SD_STATUS_CLOSED),
                )
                withPage(QueryPage.create {
                    withSkipCount(state.patchedElements.toInt())
                    withMaxItems(BATCH_SIZE)
                })
                withSortBy(SortBy(RecordConstants.ATT_CREATED, true))
                    .build()
            }
        ).getRecords()

        if (foundSdRequests.isEmpty()) {
            log.info { "No records found for patch, patch completed with $state" }
            return PatchExecutionState(state, true)
        }

        log.info { "Found ${foundSdRequests.size} records for patch" }

        foundSdRequests.forEach { sdRef ->

            getSla(sdRef)?.let { sla ->
                val atts = RecordAtts(sdRef)
                atts.setAtt(SD_ATT_SLA_1_COMPLETED_DURATION_MS, sla.sla1Ms)
                atts.setAtt(SD_ATT_SLA_2_COMPLETED_DURATION_MS, sla.sla2Ms)

                log.info { "Patching $sdRef with sla1Ms=${sla.sla1Ms}, sla2Ms=${sla.sla2Ms}" }

                recordsService.mutate(atts)
            }
        }

        val patchedElements = state.patchedElements + foundSdRequests.size

        log.info { "Total patched: $patchedElements" }

        return PatchExecutionState(
            PatchState(
                patchedElements = patchedElements
            ),
            false
        )
    }

    private fun getSla(sdRef: EntityRef): SdSla? {
        return recordsService.queryOne(
            RecordsQuery.create {
                withSourceId("service-desk/sd-sla")
                withQuery(
                    mapOf("record" to sdRef)
                )
            },
            SdSla::class.java
        )
    }

    private data class SdSla(

        @AttName("sla1Info.durationMs?num!0")
        val sla1Ms: Long = 0,

        @AttName("sla2Info.durationMs?num!0")
        val sla2Ms: Long = 0
    )

    data class PatchState(
        var patchedElements: Long = 0
    )
}
