package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.Objects;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, ThreadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(aBoolean);
    }

    @Override
    public void unlock() {
        String id = ID_PREFIX + Thread.currentThread().getId();
        if (Objects.equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name), id))
            stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
