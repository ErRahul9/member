package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.util.Util
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class RecencyConsumerTest {

    var redisConnectionRecency: StatefulRedisClusterConnection<String, String> = mock()
    var recencyAsyncCommands: RedisAdvancedClusterCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    val appConfig = AppConfig()
    var util: Util = mock()

    @Before
    fun init() {
        whenever(redisConnectionRecency.sync()).thenReturn(recencyAsyncCommands)
        whenever(util.getEpochInMillis()).thenReturn(43534543)
    }

    @Test
    fun processVastImpressionRecencyMessage() {
        val message =
            "{\"guid\":\"2bc13cf7-f437-39cc-9b39-46ac6dd9ff48\",\"advertiser_id\":30619,\"ad_served_id\":\"4f21a6f8-5c42-42d5-9175-deedca551e62\",\"campaign_id\":\"177202\",\"campaign_group_id\":\"33847\",\"creative_id\":\"3600306\",\"domain\":\"com.fubotv.roku.foxnews\",\"exchange_id\":\"148\",\"group_id\":436718,\"deal_id\":\"UFR_BWAX_MNTN_17_RON_MG_0801\",\"td_impression_id\":\"1667501289176812.2940982169.17135.steelhouse\",\"root_video\":\"/30619/JTb9FMbl6/\",\"epoch\":1667508238375,\"datetime\":\"2022-11-03 20:43:58\",\"ip\":\"173.94.245.18\",\"useragent\":{\"is_mobile_device\":false,\"browser\":\"UNKNOWN\",\"operating_system\":\"ROKU\",\"device_type\":\"DMR\",\"browser_version\":\"N/A\"},\"useragent_raw\":\"Roku/DVP-11.5 (11.5.0.4235-C2)\"}"

//        whenever(recencyAsyncCommands.evalsha<String>(appConfig.recencySha,
//            ScriptOutputType.VALUE, array, "30619", "980809676786",
//            "Any()", appConfig.recencyDeviceIDTTLSeconds.toString())).thenReturn(Any())

        val consumer = RecencyConsumer(meterRegistry, appConfig, util, redisConnectionRecency)

        val recencyMessage = consumer.extractRecency(message)

        Assert.assertEquals(recencyMessage.advertiserID, 30619)
        Assert.assertEquals(recencyMessage.ip, "173.94.245.18")
        Assert.assertEquals(recencyMessage.epoch, 1667508238375)
    }

    @Test
    fun storeVastImpressionRecencyMessage() {
        val message =
            "{\"guid\":\"2bc13cf7-f437-39cc-9b39-46ac6dd9ff48\",\"advertiser_id\":30619,\"ad_served_id\":\"4f21a6f8-5c42-42d5-9175-deedca551e62\",\"campaign_id\":\"177202\",\"campaign_group_id\":\"33847\",\"creative_id\":\"3600306\",\"domain\":\"com.fubotv.roku.foxnews\",\"exchange_id\":\"148\",\"group_id\":436718,\"deal_id\":\"UFR_BWAX_MNTN_17_RON_MG_0801\",\"td_impression_id\":\"1667501289176812.2940982169.17135.steelhouse\",\"root_video\":\"/30619/JTb9FMbl6/\",\"epoch\":1667508238375,\"datetime\":\"2022-11-03 20:43:58\",\"ip\":\"173.94.245.18\",\"useragent\":{\"is_mobile_device\":false,\"browser\":\"UNKNOWN\",\"operating_system\":\"ROKU\",\"device_type\":\"DMR\",\"browser_version\":\"N/A\"},\"useragent_raw\":\"Roku/DVP-11.5 (11.5.0.4235-C2)\"}"

        val array = arrayOf("173.94.245.18_vast")

        appConfig.recencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.recencyDeviceIDTTLSeconds = 604800
        appConfig.recencyExpirationWindowMilliSeconds = 55444

        val consumer = RecencyConsumer(meterRegistry, appConfig, util, redisConnectionRecency)

        val consumerRecord: ConsumerRecord<String, String> = mock()
        whenever(consumerRecord.value()).thenReturn(message)
        whenever(consumerRecord.topic()).thenReturn("vast_impression")

        consumer.consume(consumerRecord)

        runBlocking {
            delay(1000)
        }

        val window = util.getEpochInMillis() - appConfig.recencyExpirationWindowMilliSeconds!!
        Mockito.verify(recencyAsyncCommands).evalsha<String>(
            appConfig.recencySha,
            ScriptOutputType.VALUE,
            array,
            "30619",
            "1667508238375",
            window.toString(),
            appConfig.recencyDeviceIDTTLSeconds.toString(),
        )
    }

    @Test
    fun processGuidv2RecencyMessage() {
        val message =
            "{\"AID\":32794,\"IP\":\"67.85.61.56\",\"ORIGINAL_IP\":\"67.85.61.56\",\"IS_NEW\":false,\"IS_CONTROL_GROUP\":false,\"REFERER\":\"https://www.backmarket.com/en-us/p/iphone-se-2020-64-gb-black-unlocked/aa9551de-d3f1-4037-a2d4-5fc1893b2301#l=11\",\"IS_COOKIED\":false,\"USER_AGENT\":{\"browser\":\"SAFARI\",\"browser_version\":\"15.4\",\"operating_system\":\"MAC_OS_X\",\"device_type\":\"COMPUTER\",\"is_mobile_device\":\"false\",\"raw\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15\",\"advanced\":\"{\\\"DeviceClass\\\":\\\"Desktop\\\",\\\"DeviceName\\\":\\\"Apple Macintosh\\\",\\\"DeviceBrand\\\":\\\"Apple\\\",\\\"DeviceCpu\\\":\\\"Intel\\\",\\\"DeviceCpuBits\\\":\\\"64\\\",\\\"OperatingSystemClass\\\":\\\"Desktop\\\",\\\"OperatingSystemName\\\":\\\"Mac OS X\\\",\\\"OperatingSystemVersion\\\":\\\"10.15.7\\\",\\\"LayoutEngineClass\\\":\\\"Browser\\\",\\\"LayoutEngineName\\\":\\\"AppleWebKit\\\",\\\"LayoutEngineVersion\\\":\\\"605.1.15\\\",\\\"AgentClass\\\":\\\"Browser\\\",\\\"AgentName\\\":\\\"Safari\\\",\\\"AgentVersion\\\":\\\"15.4\\\"}\"},\"QUERY_STRING\":\"mntnis=67.85.61.56&ga_tracking_id=UA-55864326-6&ga_client_id=493775978.1667506324&shpt=iPhone%20SE%20(2020)%2064%20GB%20-%20Black%20-%20Unlocked%20%7C%20Back%20Market&ga_info=%7B%22status%22%3A%22OK%22%2C%22ga_tracking_id%22%3A%22UA-55864326-6%22%2C%22ga_client_id%22%3A%22493775978.1667506324%22%2C%22shpt%22%3A%22iPhone%20SE%20(2020)%2064%20GB%20-%20Black%20-%20Unlocked%20%7C%20Back%20Market%22%2C%22dcm_cid%22%3A%221667508492.2%22%2C%22dcm_gid%22%3A%221847498971.1667506324%22%2C%22mntnis%22%3A%22UR6R3BdYJurO%2BZ2sDCurmshD%2B2x0cRre%22%2C%22execution_workflow%22%3A%7B%22iteration%22%3A5%2C%22shpt%22%3A%22OK%22%2C%22dcm_cid%22%3A%22OK%22%2C%22dcm_gid%22%3A%22OK%22%7D%7D&dcm_cid=1667508492.2&dcm_gid=1847498971.1667506324&dxver=4.0.0&shaid=32794&plh=https%3A%2F%2Fwww.backmarket.com%2Fen-us%2Fp%2Fiphone-se-2020-64-gb-black-unlocked%2Faa9551de-d3f1-4037-a2d4-5fc1893b2301%23l%3D11&shpi=https%3A%2F%2Fd1eh9yux7w8iql.cloudfront.net%2Ffront%2Fpublic%2Fstatics%2Fpastrami%2Fimg%2Fsocials%2Ftwitter.svg&shpn=iPhone%20SE%20(2020)%20%20%20%20%20%20%20%20%20%2064%20GB%20-%20Black%20-%20Unlocked&shps=iPhoneSE202064GB-Black-Unlocked&shpc=iPhone%20SE%20(2020)%2064GB%20-%20Black%20-%20Fully%20unlocked%20(GSM%20%26%20CDMA)&shadditional=googletagmanager%3Dtrue%2Cga4%3Dtrue&shguid=cab87885-3ed6-3ae4-aaba-430b06e18924&shgts=1667506334043\",\"CACHE_BUSTER\":\"1667508514896762\",\"MOBILE\":false,\"OTHER\":\"{\\\"shgts\\\":\\\"1667506334043\\\",\\\"dcm_cid\\\":\\\"1667508492.2\\\",\\\"dxver\\\":\\\"4.0.0\\\",\\\"ga_info\\\":\\\"{\\\\\\\"status\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"ga_tracking_id\\\\\\\":\\\\\\\"UA-55864326-6\\\\\\\",\\\\\\\"ga_client_id\\\\\\\":\\\\\\\"493775978.1667506324\\\\\\\",\\\\\\\"shpt\\\\\\\":\\\\\\\"iPhone SE (2020) 64 GB - Black - Unlocked | Back Market\\\\\\\",\\\\\\\"dcm_cid\\\\\\\":\\\\\\\"1667508492.2\\\\\\\",\\\\\\\"dcm_gid\\\\\\\":\\\\\\\"1847498971.1667506324\\\\\\\",\\\\\\\"mntnis\\\\\\\":\\\\\\\"UR6R3BdYJurO+Z2sDCurmshD+2x0cRre\\\\\\\",\\\\\\\"execution_workflow\\\\\\\":{\\\\\\\"iteration\\\\\\\":5,\\\\\\\"shpt\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"dcm_cid\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"dcm_gid\\\\\\\":\\\\\\\"OK\\\\\\\"}}\\\",\\\"shguid\\\":\\\"cab87885-3ed6-3ae4-aaba-430b06e18924\\\",\\\"dcm_gid\\\":\\\"1847498971.1667506324\\\"}\",\"PRODUCT\":{\"CATEGORY\":\"iPhone SE (2020) 64GB - Black - Fully unlocked (GSM & CDMA)\",\"NAME\":\"iPhone SE (2020)          64 GB - Black - Unlocked\",\"IMG_URL\":\"https://d1eh9yux7w8iql.cloudfront.net/front/public/statics/pastrami/img/socials/twitter.svg\",\"SKU\":\"iPhoneSE202064GB-Black-Unlocked\",\"CURRENCY\":\"USD\",\"REFERRER\":\"https://www.backmarket.com/en-us/p/iphone-se-2020-64-gb-black-unlocked/aa9551de-d3f1-4037-a2d4-5fc1893b2301#l=11\"},\"CART\":{\"ITEMS\":[],\"PRODUCTS\":[]},\"GA_ID\":\"493775978.1667506324\",\"GA\":{},\"GUID\":\"cab87885-3ed6-3ae4-aaba-430b06e18924\",\"EPOCH\":1667508514896762}"

        val consumer = RecencyConsumer(meterRegistry, appConfig, util, redisConnectionRecency)

        val recencyMessage = consumer.extractRecency(message)

        Assert.assertEquals(recencyMessage.advertiserID, 32794)
        Assert.assertEquals(recencyMessage.ip, "67.85.61.56")
        Assert.assertEquals(recencyMessage.epoch, 1667508514896762)
    }

    @Test
    fun storeGuidv2RecencyMessage() {
        val message =
            "{\"AID\":32794,\"IP\":\"67.85.61.56\",\"ORIGINAL_IP\":\"67.85.61.56\",\"IS_NEW\":false,\"IS_CONTROL_GROUP\":false,\"REFERER\":\"https://www.backmarket.com/en-us/p/iphone-se-2020-64-gb-black-unlocked/aa9551de-d3f1-4037-a2d4-5fc1893b2301#l=11\",\"IS_COOKIED\":false,\"USER_AGENT\":{\"browser\":\"SAFARI\",\"browser_version\":\"15.4\",\"operating_system\":\"MAC_OS_X\",\"device_type\":\"COMPUTER\",\"is_mobile_device\":\"false\",\"raw\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15\",\"advanced\":\"{\\\"DeviceClass\\\":\\\"Desktop\\\",\\\"DeviceName\\\":\\\"Apple Macintosh\\\",\\\"DeviceBrand\\\":\\\"Apple\\\",\\\"DeviceCpu\\\":\\\"Intel\\\",\\\"DeviceCpuBits\\\":\\\"64\\\",\\\"OperatingSystemClass\\\":\\\"Desktop\\\",\\\"OperatingSystemName\\\":\\\"Mac OS X\\\",\\\"OperatingSystemVersion\\\":\\\"10.15.7\\\",\\\"LayoutEngineClass\\\":\\\"Browser\\\",\\\"LayoutEngineName\\\":\\\"AppleWebKit\\\",\\\"LayoutEngineVersion\\\":\\\"605.1.15\\\",\\\"AgentClass\\\":\\\"Browser\\\",\\\"AgentName\\\":\\\"Safari\\\",\\\"AgentVersion\\\":\\\"15.4\\\"}\"},\"QUERY_STRING\":\"mntnis=67.85.61.56&ga_tracking_id=UA-55864326-6&ga_client_id=493775978.1667506324&shpt=iPhone%20SE%20(2020)%2064%20GB%20-%20Black%20-%20Unlocked%20%7C%20Back%20Market&ga_info=%7B%22status%22%3A%22OK%22%2C%22ga_tracking_id%22%3A%22UA-55864326-6%22%2C%22ga_client_id%22%3A%22493775978.1667506324%22%2C%22shpt%22%3A%22iPhone%20SE%20(2020)%2064%20GB%20-%20Black%20-%20Unlocked%20%7C%20Back%20Market%22%2C%22dcm_cid%22%3A%221667508492.2%22%2C%22dcm_gid%22%3A%221847498971.1667506324%22%2C%22mntnis%22%3A%22UR6R3BdYJurO%2BZ2sDCurmshD%2B2x0cRre%22%2C%22execution_workflow%22%3A%7B%22iteration%22%3A5%2C%22shpt%22%3A%22OK%22%2C%22dcm_cid%22%3A%22OK%22%2C%22dcm_gid%22%3A%22OK%22%7D%7D&dcm_cid=1667508492.2&dcm_gid=1847498971.1667506324&dxver=4.0.0&shaid=32794&plh=https%3A%2F%2Fwww.backmarket.com%2Fen-us%2Fp%2Fiphone-se-2020-64-gb-black-unlocked%2Faa9551de-d3f1-4037-a2d4-5fc1893b2301%23l%3D11&shpi=https%3A%2F%2Fd1eh9yux7w8iql.cloudfront.net%2Ffront%2Fpublic%2Fstatics%2Fpastrami%2Fimg%2Fsocials%2Ftwitter.svg&shpn=iPhone%20SE%20(2020)%20%20%20%20%20%20%20%20%20%2064%20GB%20-%20Black%20-%20Unlocked&shps=iPhoneSE202064GB-Black-Unlocked&shpc=iPhone%20SE%20(2020)%2064GB%20-%20Black%20-%20Fully%20unlocked%20(GSM%20%26%20CDMA)&shadditional=googletagmanager%3Dtrue%2Cga4%3Dtrue&shguid=cab87885-3ed6-3ae4-aaba-430b06e18924&shgts=1667506334043\",\"CACHE_BUSTER\":\"1667508514896762\",\"MOBILE\":false,\"OTHER\":\"{\\\"shgts\\\":\\\"1667506334043\\\",\\\"dcm_cid\\\":\\\"1667508492.2\\\",\\\"dxver\\\":\\\"4.0.0\\\",\\\"ga_info\\\":\\\"{\\\\\\\"status\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"ga_tracking_id\\\\\\\":\\\\\\\"UA-55864326-6\\\\\\\",\\\\\\\"ga_client_id\\\\\\\":\\\\\\\"493775978.1667506324\\\\\\\",\\\\\\\"shpt\\\\\\\":\\\\\\\"iPhone SE (2020) 64 GB - Black - Unlocked | Back Market\\\\\\\",\\\\\\\"dcm_cid\\\\\\\":\\\\\\\"1667508492.2\\\\\\\",\\\\\\\"dcm_gid\\\\\\\":\\\\\\\"1847498971.1667506324\\\\\\\",\\\\\\\"mntnis\\\\\\\":\\\\\\\"UR6R3BdYJurO+Z2sDCurmshD+2x0cRre\\\\\\\",\\\\\\\"execution_workflow\\\\\\\":{\\\\\\\"iteration\\\\\\\":5,\\\\\\\"shpt\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"dcm_cid\\\\\\\":\\\\\\\"OK\\\\\\\",\\\\\\\"dcm_gid\\\\\\\":\\\\\\\"OK\\\\\\\"}}\\\",\\\"shguid\\\":\\\"cab87885-3ed6-3ae4-aaba-430b06e18924\\\",\\\"dcm_gid\\\":\\\"1847498971.1667506324\\\"}\",\"PRODUCT\":{\"CATEGORY\":\"iPhone SE (2020) 64GB - Black - Fully unlocked (GSM & CDMA)\",\"NAME\":\"iPhone SE (2020)          64 GB - Black - Unlocked\",\"IMG_URL\":\"https://d1eh9yux7w8iql.cloudfront.net/front/public/statics/pastrami/img/socials/twitter.svg\",\"SKU\":\"iPhoneSE202064GB-Black-Unlocked\",\"CURRENCY\":\"USD\",\"REFERRER\":\"https://www.backmarket.com/en-us/p/iphone-se-2020-64-gb-black-unlocked/aa9551de-d3f1-4037-a2d4-5fc1893b2301#l=11\"},\"CART\":{\"ITEMS\":[],\"PRODUCTS\":[]},\"GA_ID\":\"493775978.1667506324\",\"GA\":{},\"GUID\":\"cab87885-3ed6-3ae4-aaba-430b06e18924\",\"EPOCH\":1667508514896762}"

        val array = arrayOf("67.85.61.56")

        appConfig.recencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.recencyDeviceIDTTLSeconds = 604800
        appConfig.recencyExpirationWindowMilliSeconds = 55444

        val consumer = RecencyConsumer(meterRegistry, appConfig, util, redisConnectionRecency)

        val consumerRecord: ConsumerRecord<String, String> = mock()
        whenever(consumerRecord.value()).thenReturn(message)
        whenever(consumerRecord.topic()).thenReturn("guidv2")

        consumer.consume(consumerRecord)

        runBlocking {
            delay(1000)
        }

        val window = util.getEpochInMillis() - appConfig.recencyExpirationWindowMilliSeconds!!
        Mockito.verify(recencyAsyncCommands).evalsha<String>(
            appConfig.recencySha,
            ScriptOutputType.VALUE,
            array,
            "32794",
            "1667508514896",
            window.toString(),
            appConfig.recencyDeviceIDTTLSeconds.toString(),
        )
    }
}
