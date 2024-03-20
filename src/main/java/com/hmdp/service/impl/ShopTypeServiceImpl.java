package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<ShopType> queryTypeList() {
        List<String> list = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_KEY+":", 0, 9);
        //存在
        if (list != null &&!list.isEmpty()) {
            return  JSONUtil.toList(list.get(0), ShopType.class);
        }
        //不存在
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if (shopTypes== null || shopTypes.isEmpty()){
            return null;
        }
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_KEY+":",JSONUtil.toJsonStr(shopTypes));
        return shopTypes;
    }
}
