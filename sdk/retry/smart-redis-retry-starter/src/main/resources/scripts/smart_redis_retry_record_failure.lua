local KEY_RETRY_INFO = 1

local ARGV_NOW_MILLIS = 1
local ARGV_TTL_MILLIS = 2
local ARGV_MAX_RETRY_TIMES = 3
local ARGV_RETRY_INTERVAL_MILLIS = 4
local ARGV_MAX_INTERVAL_MILLIS = 5
local ARGV_BACKOFF_MULTIPLIER = 6
local ARGV_JITTER_RATIO = 7
local ARGV_RETRY_KEY_HASH = 8
local ARGV_LAST_ERROR_CODE = 9
local ARGV_LAST_ERROR_MESSAGE = 10
local ARGV_CONTEXT = 11

local FIELD_COUNT = 'count'
local FIELD_MAX_RETRY_TIMES = 'maxRetryTimes'
local FIELD_RETRY_INTERVAL_MILLIS = 'retryIntervalMillis'
local FIELD_MAX_INTERVAL_MILLIS = 'maxIntervalMillis'
local FIELD_BACKOFF_MULTIPLIER = 'backoffMultiplier'
local FIELD_FIRST_FAIL_TIME = 'firstFailTime'
local FIELD_LAST_FAIL_TIME = 'lastFailTime'
local FIELD_NEXT_RETRY_TIME = 'nextRetryTime'
local FIELD_LAST_ERROR_CODE = 'lastErrorCode'
local FIELD_LAST_ERROR_MESSAGE = 'lastErrorMessage'
local FIELD_CONTEXT = 'context'

local COUNT_INITIAL = 0
local COUNT_INCREMENT = 1
local RETRY_COUNT_INITIAL = 1
local JAVA_HASH_MULTIPLIER = 31
local INTEGER_32_MODULUS = 4294967296
local INTEGER_32_SIGNED_BOUNDARY = 2147483648
local JITTER_HASH_SEPARATOR = '#'
local JITTER_RANGE_MULTIPLIER = 2
local JITTER_RANGE_OFFSET = 1
local MAX_LUA_SAFE_INTEGER = 9007199254740991

local count = redis.call('HGET', KEYS[KEY_RETRY_INFO], FIELD_COUNT)
if count == false or count == nil then
    count = COUNT_INITIAL
else
    count = tonumber(count)
end

local newCount = count + COUNT_INCREMENT
local firstFailTime = redis.call('HGET', KEYS[KEY_RETRY_INFO], FIELD_FIRST_FAIL_TIME)
if firstFailTime == false or firstFailTime == nil then
    firstFailTime = ARGV[ARGV_NOW_MILLIS]
end

local nowMillis = tonumber(ARGV[ARGV_NOW_MILLIS])
local ttlMillis = tonumber(ARGV[ARGV_TTL_MILLIS])
local retryIntervalMillis = tonumber(ARGV[ARGV_RETRY_INTERVAL_MILLIS])
local maxIntervalMillis = tonumber(ARGV[ARGV_MAX_INTERVAL_MILLIS])
local backoffMultiplier = tonumber(ARGV[ARGV_BACKOFF_MULTIPLIER])
local jitterRatio = tonumber(ARGV[ARGV_JITTER_RATIO])
local maxSafeInterval = math.floor((MAX_LUA_SAFE_INTEGER - nowMillis) / JITTER_RANGE_MULTIPLIER)
local effectiveMaxInterval = math.min(maxIntervalMillis, maxSafeInterval)

local function signed32(value)
    value = value % INTEGER_32_MODULUS
    if value >= INTEGER_32_SIGNED_BOUNDARY then
        value = value - INTEGER_32_MODULUS
    end
    return value
end

local function javaHashAppend(hash, suffix)
    local result = tonumber(hash)
    for i = RETRY_COUNT_INITIAL, string.len(suffix) do
        result = signed32(result * JAVA_HASH_MULTIPLIER + string.byte(suffix, i))
    end
    return result
end

local cappedInterval = math.min(retryIntervalMillis, effectiveMaxInterval)
for retryIndex = RETRY_COUNT_INITIAL + COUNT_INCREMENT, newCount do
    if cappedInterval >= effectiveMaxInterval / backoffMultiplier then
        cappedInterval = effectiveMaxInterval
        break
    end
    cappedInterval = math.floor(cappedInterval * backoffMultiplier)
end
local jitterMillis = math.floor(cappedInterval * jitterRatio)
local currentInterval = cappedInterval
if jitterMillis > COUNT_INITIAL then
    local hash = javaHashAppend(ARGV[ARGV_RETRY_KEY_HASH], JITTER_HASH_SEPARATOR .. tostring(newCount))
    if hash < COUNT_INITIAL then
        hash = -hash
    end
    local range = jitterMillis * JITTER_RANGE_MULTIPLIER + JITTER_RANGE_OFFSET
    currentInterval = cappedInterval + (hash % range) - jitterMillis
end
local nextRetryTime = nowMillis + currentInterval

redis.call('HMSET', KEYS[KEY_RETRY_INFO],
    FIELD_COUNT, tostring(newCount),
    FIELD_MAX_RETRY_TIMES, ARGV[ARGV_MAX_RETRY_TIMES],
    FIELD_RETRY_INTERVAL_MILLIS, ARGV[ARGV_RETRY_INTERVAL_MILLIS],
    FIELD_MAX_INTERVAL_MILLIS, ARGV[ARGV_MAX_INTERVAL_MILLIS],
    FIELD_BACKOFF_MULTIPLIER, ARGV[ARGV_BACKOFF_MULTIPLIER],
    FIELD_FIRST_FAIL_TIME, firstFailTime,
    FIELD_LAST_FAIL_TIME, ARGV[ARGV_NOW_MILLIS],
    FIELD_NEXT_RETRY_TIME, tostring(nextRetryTime),
    FIELD_LAST_ERROR_CODE, ARGV[ARGV_LAST_ERROR_CODE],
    FIELD_LAST_ERROR_MESSAGE, ARGV[ARGV_LAST_ERROR_MESSAGE],
    FIELD_CONTEXT, ARGV[ARGV_CONTEXT]
)
redis.call('PEXPIRE', KEYS[KEY_RETRY_INFO], tostring(ttlMillis))

return {
    tostring(newCount),
    ARGV[ARGV_MAX_RETRY_TIMES],
    ARGV[ARGV_RETRY_INTERVAL_MILLIS],
    ARGV[ARGV_MAX_INTERVAL_MILLIS],
    ARGV[ARGV_BACKOFF_MULTIPLIER],
    firstFailTime,
    ARGV[ARGV_NOW_MILLIS],
    tostring(nextRetryTime),
    ARGV[ARGV_LAST_ERROR_CODE],
    ARGV[ARGV_LAST_ERROR_MESSAGE],
    ARGV[ARGV_CONTEXT]
}
