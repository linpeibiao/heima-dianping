package com.xiaohu.heimadianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohu.heimadianping.common.Result;
import com.xiaohu.heimadianping.domain.TbSeckillVoucher;
import com.xiaohu.heimadianping.domain.TbVoucherOrder;
import com.xiaohu.heimadianping.mapper.TbVoucherOrderMapper;
import com.xiaohu.heimadianping.service.TbSeckillVoucherService;
import com.xiaohu.heimadianping.service.TbVoucherOrderService;
import com.xiaohu.heimadianping.util.IdGenerateWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.xiaohu.heimadianping.constant.RedisConstant.SECKILL_STOCK_KEY;
import static com.xiaohu.heimadianping.constant.RedisConstant.SECKILL_STOCK_TTL;

/**
* @author xiaohu
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2022-11-02 21:04:16
*/
@Service
public class TbVoucherOrderServiceImpl extends ServiceImpl<TbVoucherOrderMapper, TbVoucherOrder>
    implements TbVoucherOrderService {

    @Autowired
    private TbSeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 引入id生成器
     */
    @Autowired
    private IdGenerateWorker worker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result secKillVoucher(Long id) {
        // 判空
        if (id == null || id < 0){
            return Result.fail("id 不合法");
        }
        // 尝试获取锁
        String key = SECKILL_STOCK_KEY + id;
        // TODO value应该是用户ID，在这里先写死
        String value = "user_id_" + 1;
        try{
            Boolean isTryLock = redisTemplate.opsForValue().setIfAbsent(key, value);
            redisTemplate.expire(key, SECKILL_STOCK_TTL, TimeUnit.SECONDS);
            // 判断是否获取锁成功，继续操作
            if (!Boolean.TRUE.equals(isTryLock)){
                // TODO 等待尝试继续获取锁
                return Result.fail("获取锁失败");
            }
            // 获取锁成功，继续操作
            QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
            wrapper.eq("voucher_id", id)
                    .eq("user_id", 1L);
            if (!list(wrapper).isEmpty()){
                return Result.fail("不能重复下单");
            }
            // 查询优惠卷
            TbSeckillVoucher voucher = seckillVoucherService.getById(id);
            // 判断库存是否充足
            if (voucher == null || voucher.getStock() < 1){
                return Result.fail("库存不足");
            }
            int stock = voucher.getStock();
            // 减库存
            boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id", id) // where voucher_id = id
                    .eq("stock", stock) // 版本号要一致
                    .gt("stock", 0) // where stock > 0
                    .update();
            if (!isSuccess){
                Result.fail("秒杀失败，库存不足");
            }
            // 生成订单信息
            TbVoucherOrder voucherOrder = new TbVoucherOrder();
            voucherOrder.setUserId(1L);
            voucherOrder.setVoucherId(id);
            // 使用订单生成算法工具类实现
            voucherOrder.setId(worker.nextId("order:"));
            // 保存订单
            save(voucherOrder);
            // 返回订单号
            return Result.ok(voucherOrder.getId());
        }finally {
            // 操作结束 释放锁
            // 只能释放自己的锁
            // 释放锁还得是原子操作，那么还得写lua脚本
            String lock = (String) redisTemplate.opsForValue().get(key);
            if (value.equals(lock)) {
                redisTemplate.delete(key);
            }
        }

    }



//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Result secKillVoucher(Long id) {
//        // 判空
//        if (id == null || id < 0){
//            return Result.fail("id 不合法");
//        }
//        // 获取当前登录用户
//        // 判断用户是否已经下单
//        QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
//        wrapper.eq("voucher_id", id)
//                .eq("user_id", 1L);
//        if (!list(wrapper).isEmpty()){
//            return Result.fail("不能重复下单");
//        }
//        // 查询优惠卷
//        TbSeckillVoucher voucher = seckillVoucherService.getById(id);
//        // 判断库存是否充足
//        if (voucher == null || voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//        int stock = voucher.getStock();
//        // 减库存
//        boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
//                .eq("voucher_id", id) // where voucher_id = id
//                .eq("stock", stock) // 版本号要一致
//                .gt("stock", 0) // where stock > 0
//                .update();
//        if (!isSuccess){
//            Result.fail("秒杀失败，库存不足");
//        }
//        // 生成订单信息
//        TbVoucherOrder voucherOrder = new TbVoucherOrder();
//        voucherOrder.setUserId(1L);
//        voucherOrder.setVoucherId(id);
//        // 使用订单生成算法工具类实现
//        voucherOrder.setId(worker.nextId("order:"));
//        // 保存订单
//        save(voucherOrder);
//        // 返回订单号
//        return Result.ok(voucherOrder.getId());
//    }
}




