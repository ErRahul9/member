import json
import os
import time

import pytest

from helpers.aero import AerospikeDataAccess
# from helpers.aero import AerospikeDataAccess
from helpers.kafka_messages import MembershipMessage

config = {
    'hosts': [('127.0.0.1', 3000)]
}



jsonfile = "fixtures/membership_updates.json"
with open(jsonfile) as jsonFile:
    test_cases = json.load(jsonFile)

testSet = []

@pytest.mark.parametrize(
    "scenario_name,test_data",
    [(k, v) for k, v in test_cases.items() if k in testSet]
    if testSet
    else test_cases.items()
)
def test_scenarios(scenario_name,test_data):
    print(f'running test={scenario_name} and test data = {test_data}')
    if test_data.get("is_delta") == True:
        message = MembershipMessage(ip="111.12.12.3", current_segments=[1000192,1000193])
        message.send()
        time.sleep(5)
    message = MembershipMessage(ip="111.12.12.3", current_segments=test_data.get("current_segments"),is_delta=test_data.get("is_delta"))
    message.send()
    time.sleep(10)
    client = AerospikeDataAccess(config)
    client.connect()
    namespace = "rtb"
    setname = "household-profile"
    record = client.get_data(namespace, setname, "111.12.12.3")
    segments = ','.join(test_data.get("current_segments"))
    print(record.value.get("segments"))
    assert  record.value.get("segments") == segments
    client.delete_data

