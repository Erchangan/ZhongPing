package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {

        String key = RedisConstants.CACHE_SHOP_TYPE;
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(shopTypeJson)) {
            return Result.ok(JSONUtil.toList(shopTypeJson, ShopType.class));
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if(shopTypeList==null){
            return Result.fail("不存在");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList),RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypeList);


    }
}
