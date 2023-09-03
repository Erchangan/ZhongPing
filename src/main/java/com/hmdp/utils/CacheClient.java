package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    public <R, ID> R queryWithPassThough(String keyPrefix, ID id, Class<R> type, Function<ID, R> fallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //当缓存中存在商铺信息时
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //解决缓存穿透，返回空字符串
        if (json != null) {
            return null;
        }

        //当缓存中不存在商铺信息中,查询数据库
        R r = fallBack.apply(id);
        if (r == null) {
            //解决缓存穿透的问题
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }

    //逻辑过期解决缓存击穿
    //定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑删除解决缓存击穿问题
    public <R,ID> R queryLogicalExpire(String keyPrefix, ID id, Class<R> type,Function<ID,R> fallBack,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //当缓存未命中
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //反序列换
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回旧的shop信息
            return r;
        }
        //已过期，重建缓存
        //首先获取锁
        String lockKey = keyPrefix + id;
        Boolean isLock = tryLock(lockKey);
        if (isLock) {
            //获取锁成功开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建
                    //查询数据库
                    //函数式接口apply接收一个参数返回一个结果
                    R r1 = fallBack.apply(id);
                    //写入缓存
                    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r1),time,unit);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    unlock(lockKey);

                }
            });
        }
        return r;

    }

    public Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }




}