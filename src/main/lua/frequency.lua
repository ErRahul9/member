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
local validImpressionEpochs = {}

for i = 1, #impressionIdEpochs, 2 do
  local recordKey = impressionIdEpochs[i]
  local recordValue = impressionIdEpochs[i + 1]

  if tonumber(recordValue) < epochExpirationLimit then
    redis.call("HDEL", key, recordKey)
   else
    validImpressionEpochs[#validImpressionEpochs+1] = recordValue
  end
end

-- set expire on deviceId
redis.call("EXPIRE", key, deviceTTL)

-- compress map of epochs into a comma separated String
local hashedKey = "{" .. key .. "}" .. "_epochs"
if next(validImpressionEpochs) == nil then
    redis.call("DEL", hashedKey)
else
    redis.call("SET", hashedKey,  table.concat(validImpressionEpochs, ","), "EX", deviceTTL)
end


