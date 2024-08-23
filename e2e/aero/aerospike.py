from dataclasses import dataclass
import aerospike_helpers
import aerospike

@dataclass
class MyData:
    key: str
    value: dict

class AerospikeClient:

    def __init__(self, config):
        self.config = config
        self.client = None

    def connect(self):
        try:
            self.client = aerospike.client(self.config).connect()
            return True
        except aerospike.exceptions.ConnectionFailure as e:
            print(f"Error: {e}")


    def insert_or_update_data(self, namespace, set_name, key, data):
        try:
            self.client.put((namespace, set_name, key), data)
            return True
        except aerospike.exceptions.ConnectionFailure as e:
            print(f"Error: {e}")

    def delete_data(self, namespace, set_name, key):
        try:
            self.client.remove((namespace, set_name, key))
            return True
        except  aerospike.exceptions.ConnectionFailure as e:

            print(f"Record not found for deletion: {(namespace, set_name, key)}")
            return False

    def get_data(self, namespace, set_name, key):
        try:
            (key, metadata, record) = self.client.get((namespace, set_name, key))
            print(record)
            return MyData(key=key[2], value=record)
        except aerospike_helpers.NotFoundError:
            print(f"Record not found: {(namespace, set_name, key)}")
            return None

    def close(self):
        if self.client:
            self.client.close()
