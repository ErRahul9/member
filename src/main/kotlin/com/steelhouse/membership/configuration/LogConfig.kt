package com.steelhouse.membership.configuration

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class LogConfig {

    @Bean
    open fun app(): Log {
        return LogFactory.getLog(appLoggerName)
    }

    companion object {
        val appLoggerName = "app"
    }
}
