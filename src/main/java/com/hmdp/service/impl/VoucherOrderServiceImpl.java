package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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



    public VoucherOrderServiceImpl(ISeckillVoucherService iSeckillVoucherService, RedisIdWorker redisIdWorker) {
        this.iSeckillVoucherService = iSeckillVoucherService;
        this.redisIdWorker = redisIdWorker;

    }

    @Override
    public Result seckillVoucher(Long voucherId) {

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
        synchronized (uid.toString().intern()) {
           IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
           return proxy.getResult(voucherId);

        }
    }

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
