FROM alpine

ADD https://download.redis.io/redis-stable.tar.gz .
RUN tar xvzf redis-stable.tar.gz
RUN apk add --no-cache make build-base
RUN apk add --no-cache --upgrade bash
RUN cd redis-stable && make
COPY start-redis-cluster.sh /start-redis-cluster.sh
COPY config.sh /config.sh
RUN chmod +x /start-redis-cluster.sh
RUN chmod +x /config.sh

ENTRYPOINT ["/start-redis-cluster.sh"]