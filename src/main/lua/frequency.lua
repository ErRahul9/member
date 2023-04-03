local deviceId = tostring(KEYS[1])
local impressionEpoch = tonumber(ARGV[1])
local epochExpirationLimit = tonumber(ARGV[2])
local deviceTTL = tonumber(ARGV[3])
local tdImpressionId = tostring(ARGV[4])

local key = deviceId

-- write impression time to redis set
redis.call("HSET", key, tdImpressionId, impressionEpoch)

-- trim excess epochs and expired
local impressionIdEpochs = redis.call("HGETALL", key)

for i = 1, #impressionIdEpochs, 2 do
    local recordKey = impressionIdEpochs[i]
    local recordValue = impressionIdEpochs[i + 1]

    if tonumber(recordValue) < epochExpirationLimit then
        redis.call("HDEL", key, recordKey)
    end
end

-- set expire on deviceId
redis.call("EXPIRE", key, deviceTTL)
