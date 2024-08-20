import logging
import socket
from typing import Optional

from confluent_kafka import Consumer, Message, Producer, TopicPartition
from confluent_kafka.admin import AdminClient, ClusterMetadata

from .config import KAFKA_BOOSTRAP_SERVER

logger = logging.getLogger(__name__)


class KafkaClient:
    def __init__(
        self,
        bootstrap_servers: str = KAFKA_BOOSTRAP_SERVER,
        group_id: str = "default test group",
    ):
        self.conf = {
            "bootstrap.servers": bootstrap_servers,
            "group.id": group_id,
            # "auto.offset.reset": "smallest",
        }
        self.producer = Producer(self.conf)

    def get_topics(self) -> dict:
        admin_client = AdminClient(self.conf)
        cluster_metadata: ClusterMetadata = admin_client.list_topics()
        topics = cluster_metadata.topics
        return topics

    def poll_latest_message(self, topic: str, timeout: int = 2, offset:int = 1) -> Optional[Message]:
        """Gets latest message from topic"""
        consumer = Consumer(self.conf)
        # Expect only a single partition
        partition = 0
        _, offset_high = consumer.get_watermark_offsets(TopicPartition(topic, partition))
        consumer.assign([TopicPartition(topic, partition, offset_high - offset)])
        message = consumer.poll(timeout=timeout)
        consumer.close()
        return message

    def send_message(self, topic: str, value: str, flush: bool = True):
        """Sends message to a topic"""
        self.producer.produce(topic, value=value)
        if flush:
            self.producer.flush()

    def flush(self):
        """Producer flush"""
        self.producer.flush()
