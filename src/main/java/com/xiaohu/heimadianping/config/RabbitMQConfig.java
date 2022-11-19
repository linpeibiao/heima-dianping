package com.xiaohu.heimadianping.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xiaohu
 * @date 2022/11/05/ 15:42
 * @description
 */
@Configuration
public class RabbitMQConfig {

    // 秒杀服务
    public static final String SECKILL_EXCHANGE_NAME = "seckill.exchange";
    public static final String SECKILL_QUEUE_NAME = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill";
    // 秒杀服务
    // 声明
    @Bean("seckillExchange")
    public DirectExchange confirmExchange(){
        return new DirectExchange(SECKILL_EXCHANGE_NAME);
    }

    @Bean("seckillQueue")
    public Queue confirmQueue(){
        return QueueBuilder.durable(SECKILL_QUEUE_NAME).build();
    }

    // bind
    @Bean
    public Binding queueBindExchange(@Qualifier("seckillQueue") Queue confirmQueue,
                                     @Qualifier("seckillExchange") DirectExchange confirmExchange){
        return BindingBuilder.bind(confirmQueue).to(confirmExchange).with(SECKILL_ROUTING_KEY);

    }
}
