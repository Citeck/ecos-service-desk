package ru.citeck.ecos.webapp.servicedesk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication

@SpringBootApplication
@EnableDiscoveryClient
class ServiceDeskApp {

    companion object {
        const val NAME = "service-desk"

        @JvmStatic
        fun main(args: Array<String>) {
            EcosSpringApplication(ServiceDeskApp::class.java).run(*args)
        }
    }
}
