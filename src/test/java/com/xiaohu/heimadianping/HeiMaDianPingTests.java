package com.xiaohu.heimadianping;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;

/**
 * @author xiaohu
 * @date 2022/11/05/ 1:49
 * @description
 */

// @RunWith(SpringRunner.class) // 报NPE 后加的
// public class HeiMaDianPingTests { // 测试类不要public
@SpringBootTest
class HeiMaDianPingTests {
    @Resource
    private RedisTemplate redisTemplate;

    @Test
    void redisTest(){
        List<MapRecord<String, Object, Object>> list =  redisTemplate.opsForStream().read(
                Consumer.from("g1", "c1"),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
        );
        System.out.printf("", list);
    }
}
