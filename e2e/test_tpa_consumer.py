import json
import os
import time

import pytest

from e2e.helpers.aero import AerospikeDataAccess
from e2e.helpers.kafka_messages import  MembershipTpaMessage

config = {
    'hosts': [('127.0.0.1', 3000)]
}


ROOTDIR = os.path.realpath(os.path.join(os.path.dirname(__file__)))
jsonfile = os.path.join(ROOTDIR, "fixtures/tpa.json")
with open(jsonfile) as jsonFile:
    test_cases = json.load(jsonFile)


testSet = [
]

@pytest.mark.parametrize(
    "scenario_name,test_data",
    [(k, v) for k, v in test_cases.items() if k in testSet]
    if testSet
    else test_cases.items()
)
def test_scenarios(scenario_name,test_data):
    print(f'running test={scenario_name} and test data = {test_data}')
    if test_data.get("is_delta") == True:
        message = MembershipTpaMessage(ip="999.99.99.9", current_segments=[1000192,1000193])
        message.send()
        time.sleep(5)
    metaObj = test_data.get("metadata_info")

    message = MembershipTpaMessage(ip="111.11.11.1",
                                   geo_version=test_data.get("geo_version"),
                                   is_delta=test_data.get("is_delta"),
                                   metadata_info = metaObj,
                                   current_segments=test_data.get("current_segments"))

    message.send()
    print(message)
    message.send()
    time.sleep(10)
    client = AerospikeDataAccess(config)
    client.connect()
    namespace = "rtb"
    setname = "household-profile"
    record = client.get_data(namespace, setname, "111.11.11.1")
    segments = ','.join(test_data.get("current_segments"))
    print(record)
    db_obj = {key.replace('cs_', ''): value for key, value in test_data.get("metadata_info").items()}
    assert record.value.get("segments") == segments
    assert record.value.get("geo_version") == test_data.get("geo_version")
    assert record.value.get("hhs:campaign") == db_obj
    client.delete_data


