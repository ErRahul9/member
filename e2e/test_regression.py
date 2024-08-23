import os
import time

import pytest
from assertpy import assert_that

from kafkaConf.kafka_messages import MembershipMessage
from aero.aerospike import AerospikeClient
# from core.redis.membership import MembershipCache

"""
Service Script:
DataSet Service: membership-consumer or membership-consumer-oracle?
Codefresh Pipeline:
"""
config = {
    'hosts': [('127.0.0.1', 3000)]
}
# MembershipCache = 'redis://localhost:6480'
aeroConnect = AerospikeClient.connect(config).connect()

@pytest.mark.parametrize("original_segments",[888888, 999999])
def test_membership_data_loading(original_segments):
    """Assert that sending a valid payload to the correct kafkaConf queue will result in the
    appropriate data being stored in the membership cache.
    """
    try:
        # original_segments = [888888, 999999]
        message = MembershipMessage(current_segments=original_segments)
        message.send()

        time.sleep(5)
        key = ('dev', 'demo', f'{message.ip}')
        found_segments = aeroConnect.get(key)

        assert_that(len(found_segments)).is_equal_to(2)
        for segment in original_segments:
            assert_that(found_segments).contains(str(segment))
    finally:
        aeroConnect.delete_data(message.ip)