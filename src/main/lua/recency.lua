local deviceId = tostring(KEYS[1])
local aid = tostring(ARGV[1])
local recencyUpdate = tonumber(ARGV[2])
local epochExpirationLimit = tonumber(ARGV[3])
local deviceTTL = tonumber(ARGV[4])

-- check device id for recency data by aid
local recency = tonumber(redis.call("HGET", deviceId, aid))

-- update recency if value doesn't exist or recency is older than new value
if recency == nil or recency < recencyUpdate then
 	redis.call("HSET", deviceId, aid, recencyUpdate)
end

-- get all recency values for a device id
local recencyByAid = redis.call("HGETALL", deviceId)

local nextkey
local nextvalue

-- remove any recency values older than the epochExpirationLimit
for key, value in pairs(recencyByAid) do
    if key % 2 == 1 then
        nextkey = value
    else
        nextvalue = value
        if tonumber(nextvalue) < epochExpirationLimit then
		    redis.call("HDEL", deviceId, tostring(nextkey))
	    end
    end
end

-- set expire on deviceId
redis.call("EXPIRE", deviceId, deviceTTL)


