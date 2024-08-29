from dataclasses import dataclass
from typing import Any, Optional, Dict
import aerospike
from aerospike import exception as ex  # Correctly import exceptions from aerospike
from dotenv import load_dotenv


@dataclass
class AerospikeRecord:
    key: str
    value: Dict[str, Any]
class AerospikeDataAccess:
    def __init__(self, config: Dict[str, Any]):
        load_dotenv(".env.docker")
        self.config = config
        self.client = None
    def connect(self):
        try:
            self.client = aerospike.client(self.config).connect()
        except ex.ClientError as e:
            print(f"Error connecting to the Aerospike cluster: {e}")
            raise
    def insert_or_update_data(self, namespace: str, set_name: str, key: str, data: Dict[str, Any]) -> bool:
        try:
            self.client.put((namespace, set_name, key), data)
            return True
        except ex.ClientError as e:
            print(f"Error inserting/updating data: {e}")
            raise
    def get_data(self, namespace: str, set_name: str, key: str) -> Optional[AerospikeRecord]:
        try:
            key_tuple, metadata, record = self.client.get((namespace, set_name, key))
            return AerospikeRecord(key=key_tuple, value=record)
        except ex.RecordNotFound:
            print(f"Record not found: {namespace}, {set_name}, {key}")
            return None
        except ex.ClientError as e:
            print(f"Error fetching data: {e}")
            raise
    def delete_data(self, namespace: str, set_name: str, key: str) -> bool:
        try:
            self.client.remove((namespace, set_name, key))
            return True
        except ex.RecordNotFound:
            print(f"Record not found for deletion: {namespace}, {set_name}, {key}")
            return False
        except ex.ClientError as e:
            print(f"Error deleting data: {e}")
            raise
    def close(self):
        if self.client:
            self.client.close()
