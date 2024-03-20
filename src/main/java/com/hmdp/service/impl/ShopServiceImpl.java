package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
//    @Cacheable(cacheNames = "cache:shop", key = "#id")
    public Result queryById(Long id) {
        //缓存穿透.redis和数据库中都没有，返回空值
        //Shop shop = queryWithPassThrough(id);
        //解决缓存击穿，使用互斥锁
//         Shop shop = queryWithMutex(this, id);
        //解决缓存击穿，使用逻辑过期
        Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("空");
        }
        return Result.ok(shop);
    }

    public static Shop queryWithMutex(ShopServiceImpl shopService, Long id) {
        String key = "cache:shop:" + id;
        String lockKey = "lock:shop:" + id;
        while (true) {
            if (!shopService.tryLock(lockKey)) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    String shopJson = shopService.stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(shopJson)) {
                        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                        return shop;
                    }
                    if (shopJson != null) {
                        return null;
                    }

                    Shop shop = shopService.getById(id);
                    Thread.sleep(200);
                    if (shop == null) {
                        shopService.stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }
                    shopService.stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                    return shop;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    shopService.unLock(lockKey);
                }
            }
        }


    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicExpire(Long id) {
        String key = "cache:shop:" + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //命中，先吧json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        String lockKey = "lock:shop:" + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        try {
                            this.savaShopData(id, 30L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            unLock(lockKey);
                        }
                    });
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;

    }

    //    @Cacheable(cacheNames = "cache:shop", key = "#id")
    public Shop queryWithPassThrough(Long id) {
        String key = "cache:shop:" + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson.equals("")) {
            return null;
        }
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;

    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("错误ID");
        }
        updateById(shop);
        stringRedisTemplate.delete("cache:shop:" + shop.getId());
        return Result.ok();
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL + 2L, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    //设置逻辑过期时间
    public void savaShopData(Long id, Long expireTime) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireTime));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
