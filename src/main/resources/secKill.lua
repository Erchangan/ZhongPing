local voucherId=ARGV[1]
local userId=ARGV[2]
--..拼接
local stockKey ='seckill:stock:' ..voucherId
local orderKey ='seckill:order' ..voucherId
--首先判断库存是否充足
if(tonumber(redis.call('get',stockKey))<=0)then
    --库存不足返回1
    return 1
end
--判断用户是否下过单
if(redis.call('sismember',orderKey,userId)==1)then
    --存在说明下过单
    return 2
end
--扣库存
redis.call('incrby',stockKey,-1)
--保存用户
redis.call('sadd',orderKey,userId)
return 0