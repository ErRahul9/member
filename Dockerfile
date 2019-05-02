
FROM steelhousedev/alpine-run-8:1.0

ENV APPLICATION membership-consumer
COPY ./build/distributions/membership-consumer*.zip ./
RUN rm membership-consumer-boot*.zip && unzip membership-consumer*.zip && rm membership-consumer*.zip
