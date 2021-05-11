package com.steelhouse.membership.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import javax.validation.constraints.NotNull


@Configuration
@ConfigurationProperties(prefix = "app")
@ComponentScan
open class AppConfig {

    @NotNull
    var membershipDataExpirationWindowSeconds: Long? = null
}

