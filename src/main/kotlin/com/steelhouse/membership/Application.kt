package com.steelhouse.membership

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling

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
            println("Username: " + System.getenv("USERNAME"))
            println("Password: " + System.getenv("PASSWORD"))
            SpringApplication.run(Application::class.java, *args)
        }
    }
}
