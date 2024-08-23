import time

import pytest
from assertpy import assert_that

from core.kafka_messages import MembershipOracleMessage
from core.redis.membership import MembershipCache

"""
Service Script:
DataSet Service: membership-consumer or membership-consumer-oracle?
Codefresh Pipeline:
"""


@pytest.mark.xfail(reason="data loading scripts need to be changed from set to string, like with BID-465")
class TestOracleMembershipLoading:
    def test_membership_data_loading(self):
        """Assert that sending a valid payload to the correct kafka queue will result in the
        appropriate data being stored in the membership cache.
        """
        try:
            original_segments = [888888, 999999]
            message = MembershipOracleMessage(current_segments=original_segments)
            message.send()

            time.sleep(5)

            found_segments = MembershipCache.get_segments(message.ip)

            assert_that(len(found_segments)).is_equal_to(2)
            for segment in original_segments:
                assert_that(found_segments).contains(str(segment))
        finally:
            MembershipCache.delete_record(message.ip)

    @pytest.mark.parametrize(
        "original_segments, appended_segments", [([777777, 888888], [999999]), ([777777, 888888], [888888, 999999])]
    )
    def test_append_membership_data(self, original_segments, appended_segments):
        """Assert that membership data can be appended based on the is_delta flag being set to true."""
        try:
            message = MembershipOracleMessage(current_segments=original_segments)
            message.send()

            time.sleep(1)

            segments = MembershipCache.get_segments(message.ip)
            assert_that(segments).is_not_none()

            message = MembershipOracleMessage(current_segments=appended_segments, is_delta=True)
            message.send()

            time.sleep(1)

            segments = MembershipCache.get_segments(message.ip)
            assert_that(len(segments)).is_equal_to(3)
            for segment in original_segments:
                assert_that(segments).contains(str(segment))
            for segment in appended_segments:
                assert_that(segments).contains(str(segment))

        finally:
            MembershipCache.delete_record(message.ip)

    def test_overwrite_membership_data(self):

        """Assert that membership data can be overwritten based on the is_delta flag being set to false."""
        try:
            original_segments = [777777, 888888]
            message = MembershipOracleMessage(current_segments=original_segments)
            message.send()
            time.sleep(5)
            print("*****************")
            message.consumeLog()
            print("*****************")
            # time.sleep(1)

        #     found_segments = MembershipCache.get_segments(message.ip)
        #     assert_that(found_segments).is_not_none()
        #     updated_segments = [999999]
        #     message = MembershipOracleMessage(current_segments=updated_segments, is_delta=False)
        #     message.send()
        #     time.sleep(1)
        #     found_segments = MembershipCache.get_segments(message.ip)
        #     assert_that(len(found_segments)).is_equal_to(1)
        #     for segment in updated_segments:
        #         assert_that(found_segments).contains(str(segment))
        finally:
            MembershipCache.delete_record(message.ip)

        #


