package com.hmdp.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.mapper.VoucherOrderMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

       // @Override //一人一单
//    public Result seckillVoucher(Long voucherId) {
//        //首先获取优惠券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //获取开始时间与结束时间
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        if (beginTime.isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        if (endTime.isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //判处断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        //一人只能买一单
//        Long userId = UserHolder.getUser().getId();
//        //使用Redis分布式锁解决跨进程问题
////        RedisLock redisLock = new RedisLock("order:" + userId, stringRedisTemplate);
//        RLock redissonLock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = redissonLock.tryLock();
//        if (!isLock) {
//            return Result.fail("不能重复下单");
//        }
//        try {
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            redissonLock.unlock();
//        }
//    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        long orderId = redisWorker.nextId("order");
        return Result.ok(orderId);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        Long userId = UserHolder.getUser().getId();
        QueryWrapper<VoucherOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("voucher_id", voucherId);
        int count = this.count(queryWrapper);
        if (count > 0) {
            return Result.fail("用户已购买过一次");
        }
        //扣减库存
        //使用乐观锁CAS的方法解决超卖问题
        LambdaUpdateWrapper<SeckillVoucher> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        //避免乐观锁 失败率过高的问题
        lambdaUpdateWrapper.gt(SeckillVoucher::getStock, 0);
        lambdaUpdateWrapper.set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1);
        boolean success = seckillVoucherService.update(lambdaUpdateWrapper);
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
