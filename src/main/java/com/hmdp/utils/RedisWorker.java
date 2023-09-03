package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final long BEGIN_TIMESTAMP=1640995200;
    private static final long COUNT_BITS=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowTime-BEGIN_TIMESTAMP;
        //自增长
        //获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("ice:" + keyPrefix + ":" + date);
        return timestamp<<32 | count;

    }

}
