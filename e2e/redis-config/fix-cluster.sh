#!/bin/bash

sleep 10
redis-cli --cluster fix localhost:6380 --cluster-yes
redis-cli --cluster fix localhost:6381 --cluster-yes
redis-cli --cluster fix localhost:6382 --cluster-yes
redis-cli --cluster fix localhost:6383 --cluster-yes
redis-cli --cluster fix localhost:6384 --cluster-yes
redis-cli --cluster fix localhost:6386 --cluster-yes
redis-cli --cluster fix localhost:6387 --cluster-yes
redis-cli --cluster fix localhost:6388 --cluster-yes
redis-cli --cluster fix localhost:6389 --cluster-yes
