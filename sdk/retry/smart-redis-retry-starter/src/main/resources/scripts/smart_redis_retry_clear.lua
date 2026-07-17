local KEY_RETRY_INFO = 1
local ARGV_EXPECTED_COUNT = 1
local FIELD_COUNT = 'count'
local EXPECTED_COUNT_EMPTY = ''

local currentCount = redis.call('HGET', KEYS[KEY_RETRY_INFO], FIELD_COUNT)
if currentCount == false or currentCount == nil then
    return nil
end

local expectedCount = ARGV[ARGV_EXPECTED_COUNT]
if expectedCount ~= EXPECTED_COUNT_EMPTY and currentCount ~= expectedCount then
    return nil
end

local values = redis.call('HGETALL', KEYS[KEY_RETRY_INFO])
redis.call('DEL', KEYS[KEY_RETRY_INFO])
return values
