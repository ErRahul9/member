# Use an official Python runtime as a parent image
FROM python:3.11-slim-buster



# Set the working directory to /app
WORKDIR /app

# Copy the pyproject.toml and poetry.lock files to the container
COPY poetry.lock /app/

COPY pyproject.toml /app/


RUN pip install psycopg2-binary

# Install Poetry
RUN pip install --no-cache-dir poetry

# Install dependencies using Poetry
RUN poetry config virtualenvs.create false && \
    poetry install --no-interaction --no-ansi


RUN poetry add protobuf
# Set the environment variable for pytest to run in non-interactive mode
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

COPY . .

#COPY .. . use this to run the tests in local docker container



CMD ["poetry", "run", "pytest","test_membership_consumer.py","--html=report_regression.html"]
CMD ["poetry", "run", "pytest","test_tpa_consumer.py","--html=report_regression.html"]

#CMD ["poetry", "run", "pytest","test_single_run.py","--html=report_regression.html"]
