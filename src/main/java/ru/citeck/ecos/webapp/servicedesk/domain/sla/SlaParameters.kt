package ru.citeck.ecos.webapp.servicedesk.domain.sla

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.servicedesk.domain.request.SdPriority
import kotlin.time.Duration

@Component
class SlaParametersProvider(
    private val recordsService: RecordsService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val SLA_SD_TYPE_SOURCE_ID = "emodel/sd-sla-type"
    }

    fun get(client: EntityRef, priority: SdPriority): SlaParameters {
        if (client.isEmpty()) {
            error("Empty client not allowed")
        }

        return getClientSlas(client, priority)
            ?: getDefaultSla(priority)
            ?: error("No SLA found for client $client and priority $priority")
    }

    private fun getClientSlas(client: EntityRef, priority: SdPriority): SlaParameters? {
        return recordsService.querySla(
            Predicates.and(
                Predicates.eq("client", client),
                Predicates.eq("priority", priority.value)
            )
        )
    }

    private fun getDefaultSla(priority: SdPriority): SlaParameters? {
        return recordsService.querySla(
            Predicates.and(
                Predicates.empty("client"),
                Predicates.eq("priority", priority.value)
            )
        )
    }

    private fun RecordsService.querySla(predicate: Predicate): SlaParameters? {
        log.debug { "Query SLA with predicate: $predicate" }

        val foundSla = recordsService.query(
            RecordsQuery.create {
                withSourceId(SLA_SD_TYPE_SOURCE_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(predicate)
            },
            SlaParametersData::class.java
        )

        if (foundSla.getTotalCount() > 1) {
            log.warn { "Found more than one SLA for predicate=$predicate. First resul will be used." }
        }

        log.debug { "Found SLA: $foundSla" }

        return foundSla.getRecords().firstOrNull()?.toSlasParameters()
    }

}

private data class SlaParametersData(
    val timeFirstReaction: String,
    val timeResolve: String,
    val notificationToExecutorTimeReaction: String,
    val notificationToExecutorTimeResolve: String,
    val notificationToSupervisorTime: String,
    val timeToAutoClose: String,
    val timeToSendFirstLineFromClarify: String
)

private fun SlaParametersData.toSlasParameters(): SlaParameters {
    val autoCloseDuration = Duration.parse(timeToAutoClose)
    val notificationToInitiatorCloseReminder = autoCloseDuration.div(2)

    return SlaParameters(
        timeFirstReaction = Duration.parse(timeFirstReaction),
        timeResolve = Duration.parse(timeResolve),
        notificationToExecutorTimeReaction = Duration.parse(notificationToExecutorTimeReaction),
        notificationToExecutorTimeResolve = Duration.parse(notificationToExecutorTimeResolve),
        notificationToSupervisorTime = Duration.parse(notificationToSupervisorTime),
        timeToAutoClose = autoCloseDuration,
        notificationToInitiatorCloseReminder = notificationToInitiatorCloseReminder,
        timeToSendFirstLineFromClarify = Duration.parse(timeToSendFirstLineFromClarify)
    )
}

data class SlaParameters(
    val timeFirstReaction: Duration,
    val timeResolve: Duration,
    val notificationToExecutorTimeReaction: Duration,
    val notificationToExecutorTimeResolve: Duration,
    val notificationToSupervisorTime: Duration,
    val notificationToInitiatorCloseReminder: Duration,
    val timeToAutoClose: Duration,
    val timeToSendFirstLineFromClarify: Duration
)
