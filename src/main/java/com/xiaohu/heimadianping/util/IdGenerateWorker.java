package com.xiaohu.heimadianping.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @author xiaohu
 * @date 2022/11/03/ 0:11
 * @description id生成器
 */

/**
 * id 由时间戳和序列号拼接而成
 */
@Component
public class IdGenerateWorker {
    /**
     * 开始时间戳
     */
    private final static long BEGIN_TIMESTAMP = 984355200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;
    /**
     * 引入redis
     */
    @Autowired
    private RedisTemplate redisTemplate;
    public long nextId(String keyPrefix){
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long nowTimeStamp = nowTime - BEGIN_TIMESTAMP;
        // 生成序列号
        /**
         * 由于订单数量是非常多的，redis的key值应该按照日期给出，一天的订单所用的key相同
         */
        // 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 设置自增长值
        // 这里绝对不会出现空指针，如果key值不存在，会自动创建一个key
        long count = redisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);

        // 拼接时间戳和序列号返回
        /**
         * 拼接方法：不能直接进行加减，也不能使用拼接返回字符串
         * 由于序列号是32位，先将时间戳向左移动32位，那么低32位会被0填充
         * 接着与序列号进行位或运算即可。
         */
        return nowTimeStamp << COUNT_BITS | count;

    }

}
