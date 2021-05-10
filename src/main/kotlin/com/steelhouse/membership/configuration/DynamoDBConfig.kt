package com.steelhouse.membership.configuration

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration


@Configuration
@ConfigurationProperties(prefix = "dynamo")
@ComponentScan
@EnableDynamoDBRepositories
open class DynamoDBConfig {



    @Bean
    open fun amazonDynamoDB(): AmazonDynamoDB {

        return AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)
                .build()


    }

}
