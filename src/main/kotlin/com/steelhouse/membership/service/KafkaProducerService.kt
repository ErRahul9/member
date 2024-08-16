package com.steelhouse.membership.service

import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaOperations2
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class KafkaProducerService(
    kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("app") private val log: Log
) {
    private final val template: KafkaOperations2<String, String> = kafkaTemplate.usingCompletableFuture()

    fun sendMessage(topic: String, message: String): CompletableFuture<SendResult<String, String>> {
        return template.send(topic, message)
            .whenComplete { _, thrown ->
                if (thrown != null) {
                    log.error("Error sending message", thrown)
                } else {
                    log.debug("Message sent successfully")
                }
            }
    }
}
