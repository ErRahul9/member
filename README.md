# membership-consumer

## Description
Houses services which loads data points from various sources into Redis cache for bidder/augmenter to consume at high thr'put/low latency requirement.
1. membership information from Kafka membership-updates topic
2. TPA S3 bucket sh-dw-generated-audiences-prod into the membership (metadata) Redis cache.
3. writes recency/impression data for recency calculation in bidder system from vastimpression
