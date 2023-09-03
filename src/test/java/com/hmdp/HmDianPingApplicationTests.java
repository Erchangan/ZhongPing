package com.hmdp;


import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;


import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void saveShop2Redis() {
    }
    @Test
    void loadShopData(){
        //查询店铺信息
        List<Shop> list = shopService.list();
        //根据typeId分组
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //写入Redis
        for (Map.Entry<Long, List<Shop>> entry : collect.entrySet()) {
            Long typeId = entry.getKey();
            String key ="shop:geo"+typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(shops.size());
            //遍历shops得到店铺id和经纬坐标
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
