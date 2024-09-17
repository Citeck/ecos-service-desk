package ru.citeck.ecos.webapp.servicedesk.domain.sla

enum class SlaState {
    CREATED,
    RUNNING,
    PAUSE,
    COMPLETE
}

enum class SlaType {
    SLA1,
    SLA2
}
