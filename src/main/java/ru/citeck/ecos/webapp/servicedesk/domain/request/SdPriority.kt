package ru.citeck.ecos.webapp.servicedesk.domain.request

enum class SdPriority(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    URGENT("urgent");

    companion object {
        fun from(value: String): SdPriority = values().find { it.value == value }
            ?: throw IllegalArgumentException("Sd Priority: $value not found")
    }
}
