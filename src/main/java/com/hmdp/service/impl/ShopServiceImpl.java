package com.hmdp.service.impl;



import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.SuppressJava6Requirement;
import org.redisson.Redisson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;



    @Override
    public Result queryById(Long id) {
        //首先根据Id查询缓存中是否已店铺信息
        Shop shop = cacheClient.queryWithPassThough(CACHE_SHOP_KEY, id, Shop.class,
                this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //利用互斥锁解决缓存击穿的问题
    public Shop queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //当缓存中存在商铺信息时
        if (StringUtils.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //解决缓存穿透，返回空字符串
        if (shopJson != null) {
            return null;
        }
        //实现缓存重建
        //获取锁
        String lockKey = "lock:shop" + id;
        Shop shop = null;
        try {
            Boolean isTure = cacheClient.tryLock(lockKey);
            //判断获取锁是否成功
            if (!isTure) {
                //休眠一段时间，再次查询
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //当缓存中不存在商铺信息中
            shop = this.getById(id);
            if (shop == null) {
                //解决缓存穿透的问题
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            cacheClient.unlock(lockKey);
        }
        return shop;
    }



    @Override
    @Transactional
    public Result update(Shop shop) {
        //更新数据库
        this.updateById(shop);
        Long id = shop.getId();
        if (id == null) {
            Result.fail("商铺Id不能为空");
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        return null;
    }


}
