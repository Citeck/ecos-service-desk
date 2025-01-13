package ru.citeck.ecos.webapp.servicedesk.domain.request

import ru.citeck.ecos.webapp.api.constants.AppName

const val SD_TYPE = "sd-request-type"
const val SD_SOURCE_ID = "${AppName.EMODEL}/$SD_TYPE"

const val SD_ATT_SLA_1_COMPLETED_DURATION_MS = "sla_1_completed_duration_ms"
const val SD_ATT_SLA_2_COMPLETED_DURATION_MS = "sla_2_completed_duration_ms"

const val SD_STATUS_CLOSED = "request-closes"
