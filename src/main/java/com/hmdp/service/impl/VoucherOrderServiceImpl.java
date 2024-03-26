package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService iSeckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(ISeckillVoucherService iSeckillVoucherService, RedisIdWorker redisIdWorker, StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.iSeckillVoucherService = iSeckillVoucherService;
        this.redisIdWorker = redisIdWorker;

        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
           Long userId = UserHolder.getUser().getId();
         long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3.返回订单id
        return Result.ok(orderId);
    }


    /*@Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {

        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        boolean success = iSeckillVoucherService.update().setSql("stock=stock-1").gt("stock", 0).eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        Long uid = UserHolder.getUser().getId();

//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + uid);
        RLock lock = redissonClient.getLock("order:" + uid);
        boolean isLock = lock.tryLock(1,5, TimeUnit.SECONDS);
        if (!isLock) {
            return Result.fail("重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getResult(voucherId);
        } finally {
            {
                lock.unlock();
            }

   *//*     synchronized (uid.toString().intern()) {
           IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
           return proxy.getResult(voucherId);*//*

        }

    }*/

    @Transactional(rollbackFor = Exception.class)

    public Result getResult(Long voucherId) {
        Long uid = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", uid).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已存在");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);

        voucherOrder.setUserId(uid);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(id);

    }
}
