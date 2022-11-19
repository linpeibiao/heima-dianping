package com.xiaohu.heimadianping.service;

import com.xiaohu.heimadianping.domain.TbVoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.xiaohu.heimadianping.config.RabbitMQConfig.SECKILL_EXCHANGE_NAME;
import static com.xiaohu.heimadianping.config.RabbitMQConfig.SECKILL_ROUTING_KEY;

/**
 * @author xiaohu
 * @date 2022/11/05/ 17:34
 * @description
 */

@Component
@Slf4j
public class SecKillRabbitMQService {
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TbVoucherOrderService voucherOrderService;
    /**
     * 发送消息
     * @param voucherOrder 消息
     */
    public boolean sendMessage(TbVoucherOrder voucherOrder) {
        log.info("发送信息：" + voucherOrder);
        rabbitTemplate.convertAndSend(SECKILL_EXCHANGE_NAME, SECKILL_ROUTING_KEY, voucherOrder);
        return true;
    }

    /**
     *     注入 接收消息
     */
    public static final String SECKILL_QUEUE_NAME = "seckill.queue";

    /**
     *
     * @param voucherOrder
     */
    @RabbitListener(queues = SECKILL_QUEUE_NAME)
    public void receive(TbVoucherOrder voucherOrder) {
        log.info("接收信息：" + voucherOrder);
        voucherOrderService.createVoucherOrder(voucherOrder);
    }



}
