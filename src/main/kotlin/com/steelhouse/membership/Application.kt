package com.steelhouse.membership

import org.apache.kafka.common.config.SaslConfigs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component


@EnableScheduling
@SpringBootApplication(
    exclude = [
        DataSourceAutoConfiguration::class, // No JPA
        DataSourceTransactionManagerAutoConfiguration::class, HibernateJpaAutoConfiguration::class,
    ],
)
open class Application {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Application::class.java, *args)
        }
    }
}
