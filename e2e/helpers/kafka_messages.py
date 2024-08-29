import enum
import json
import os
from dataclasses import dataclass
from datetime import datetime, timedelta
from uuid import uuid4

import pytz
from kafka import KafkaProducer, KafkaConsumer
# from date_utils import epoch_in_microseconds
from dotenv import load_dotenv
import logging

logger = logging.getLogger(__name__)

def epoch_in_microseconds(timestamp=None, days=0, hours=0, minutes=0, seconds=0, milliseconds=0):
    now = timestamp or datetime.now(pytz.utc)
    time_delta = timedelta(days=days, hours=hours, minutes=minutes, seconds=seconds, milliseconds=milliseconds)
    adjusted_time = now + time_delta
    epoch_microseconds = int(adjusted_time.timestamp()) * 1000
    return epoch_microseconds

class KafkaMessage:
    def __init__(self, host, topic):
        self.host = host
        self.topic = topic
    def _create_producer(self):
        producer = KafkaProducer(
            bootstrap_servers=f'{self.host}',
            retries=5,
            value_serializer=lambda v: v.encode("utf-8"),
            request_timeout_ms=120000,  # value pulled from prod env files.
        )
        return producer

    def send(self, payload,topic):
        producer = self._create_producer()
        producer.send(topic=topic, value=payload)
        producer.flush()
        producer.close()
class MembershipMessage:
    def __init__(
        self,
        guid=None,
        advertiser_id=None,
        epoch=None,
        activity_epoch=None,
        current_segments=None,
        old_segments=None,
        segment_versions=None,
        ip=None,
        data_source=None,
        is_delta=None,
    ):
        load_dotenv(".env.docker")
        self.KAFKA_MEMBERSHIP_HOST = f'{os.getenv("KAFKA_HOST")}:{os.getenv("KAFKA_PORT")}'
        self.KAFKA_TOPIC_MEMBERSHIP_UPDATES = os.getenv("KAFKA_TOPIC_MEMBERSHIP_UPDATES")
        self.guid = guid or str(uuid4())
        self.advertiser_id = advertiser_id or 123456
        self.current_segments = current_segments or [100000, 100001]
        self.old_segments = old_segments or []
        self.activity_epoch = activity_epoch or epoch_in_microseconds()
        self.epoch = epoch or epoch_in_microseconds()
        self.segment_versions = segment_versions or []
        self.ip = ip or "111.1.1.1"
        self.data_source = data_source or 3  # must be 3 to work
        self.is_delta = is_delta or False  # true = append, false = overwrite
        self._message = KafkaMessage(self.KAFKA_MEMBERSHIP_HOST,  self.KAFKA_TOPIC_MEMBERSHIP_UPDATES)
        self.segment_versions = [
            {
                "segment_id": 100000,
                "version": 1716480462836
            },
            {
                "segment_id": 100001,
                "version": 1716480548745
            }
        ]
    def _generate_payload(self):
        payload = {
            "guid": self.guid,
            "advertiser_id": self.advertiser_id,
            "current_segments": self.current_segments,
            "old_segments": self.old_segments,
            "activity_epoch": self.activity_epoch,
            "epoch": self.epoch,
            "ip": self.ip,
            "data_source": self.data_source,
            "is_delta": self.is_delta,
            "segment_versions": self.segment_versions
        }
        return payload
    def send(self):
        payload = self._generate_payload()
        print(payload)
        self._message.send(json.dumps(payload),self.KAFKA_TOPIC_MEMBERSHIP_UPDATES)


class MembershipTpaMessage:
    def __init__(
        self,
        guid=None,
        advertiser_id=None,
        current_segments=None,
        old_segments=None,
        epoch=None,
        activity_epoch=None,
        ip=None,
        household_score=None,
        geo_version=None,
        data_source=None,
        is_delta=None,
        campaign_id=None,
        metadata_info=None,

    ):
        load_dotenv(".env.docker")
        self.KAFKA_TOPIC_TPA = os.getenv("KAFKA_TOPIC_TPA")
        self.KAFKA_MEMBERSHIP_HOST = f'{os.getenv("KAFKA_HOST")}:{os.getenv("KAFKA_PORT")}'
        self.guid = guid or str(uuid4())
        self.advertiser_id = advertiser_id or 123456
        self.current_segments = current_segments or [100000, 100001]
        self.old_segments = old_segments or []
        self.epoch = epoch or epoch_in_microseconds()
        self.activity_epoch = activity_epoch or epoch_in_microseconds()
        self.ip = ip or "999.9.9.99"
        self.household_score = household_score or 10
        self.geo_version = geo_version or 55555
        self.data_source = data_source or 3  # must be 3 to work
        self.is_delta = is_delta or False  # true = append, false = overwrite
        self.campaign_id = campaign_id or None
        self.metadata_info = metadata_info or {"cs_123": 10,"cs_321": 20}
        self._message = KafkaMessage(self.KAFKA_MEMBERSHIP_HOST,  self.KAFKA_TOPIC_TPA)

    def _compile_payload(self):
        payload = {
            "guid": self.guid,
            "advertiser_id": self.advertiser_id,
            "current_segments": self.current_segments,
            "old_segments": self.old_segments,
            "epoch": self.epoch,
            "activity_epoch": self.activity_epoch,
            "ip": self.ip,
            "data_source": self.data_source,
            "geo_version": self.geo_version,
            "metadata_info": self.metadata_info,
            "is_delta": self.is_delta,
        }
        return payload

    def send(self):
        payload = self._compile_payload()
        print(payload)
        self._message.send(json.dumps(payload),self.KAFKA_TOPIC_TPA)

