# membership-consumer

[![Codefresh build status]( https://g.codefresh.io/api/badges/pipeline/steelhouse/SteelHouse%2Fmembership-consumer%2Fqa-pr-deploy?branch=dev&key=eyJhbGciOiJIUzI1NiJ9.NWEzYjA1NTEzNzNlNDEwMDAxYzhhMDBm.v7TXkltQXeRm04GXW-KfRp0xBTdggMIZmHNi3-xP4IA&type=cf-1)]( https://g.codefresh.io/pipelines/qa-pr-deploy/builds?repoOwner=SteelHouse&repoName=membership-consumer&serviceName=SteelHouse%2Fmembership-consumer&filter=trigger:build~Build;branch:dev;pipeline:5ccb56c862ced396e937ce2d~qa-pr-deploy)
[![Coverage Status](https://coveralls.io/repos/github/SteelHouse/membership-consumer/badge.svg?branch=dev&t=fxiZTZ)](https://coveralls.io/github/SteelHouse/membership-consumer?branch=dev)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/64630f46927f4810890872dd384985a5)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=SteelHouse/membership-consumer&amp;utm_campaign=Badge_Grade)

## Description
Houses services which loads data points from various sources into Redis cache for bidder/augmenter to consume at high throughput/low latency requirement.
1. membership information from Kafka `membership-updates` topic
2. TPA S3 bucket `sh-dw-generated-audiences-prod` into the membership (metadata) Redis cache.
3. writes recency/impression data for recency calculation in bidder system from `vast_impression`

## Aerospike

### Notes

The prior Redis caches for `membership` (containing segments) and `device-info` (containing geo_version and household_score) have been replaced with Aerospike, combined under the set `household-profile`.

### Local Development

`docker run -d --name aerospike -e "NAMESPACE=rtb" -p 3000-3002:3000-3002 aerospike/aerospike-server`