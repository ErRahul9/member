import os

from dotenv import load_dotenv

load_dotenv()


KAFKA_BOOSTRAP_SERVER = os.getenv("KAFKA_BOOSTRAP_SERVER", "localhost:9092")
