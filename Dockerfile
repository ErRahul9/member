FROM steelhousedev/slim-run-11:1.1

ENV APPLICATION membership-consumer
COPY ./build/distributions/membership-consumer*.zip ./
RUN unzip membership-consumer*.zip && rm membership-consumer*.zip
