#!/bin/sh

./config.sh
/redis-stable/utils/create-cluster/create-cluster start
/redis-stable/utils/create-cluster/create-cluster create -f

tail -f /dev/null