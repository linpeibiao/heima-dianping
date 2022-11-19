package com.xiaohu.heimadianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohu.heimadianping.common.Result;
import com.xiaohu.heimadianping.domain.TbSeckillVoucher;
import com.xiaohu.heimadianping.domain.TbVoucherOrder;
import com.xiaohu.heimadianping.mapper.TbVoucherOrderMapper;
import com.xiaohu.heimadianping.service.SecKillRabbitMQService;
import com.xiaohu.heimadianping.service.TbSeckillVoucherService;
import com.xiaohu.heimadianping.service.TbVoucherOrderService;
import com.xiaohu.heimadianping.util.ILock;
import com.xiaohu.heimadianping.util.IdGenerateWorker;
import com.xiaohu.heimadianping.util.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.SpringTemplateLoader;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
* @author xiaohu
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2022-11-02 21:04:16
*/
@Service
@Slf4j
public class TbVoucherOrderServiceImpl extends ServiceImpl<TbVoucherOrderMapper, TbVoucherOrder>
    implements TbVoucherOrderService {

    @Autowired
    private TbSeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SecKillRabbitMQService secKillRabbitMQService;
    /**
     * lua脚本引导类
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    /**
     * 创建线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    /**
     * spring 容器初始化时执行该方法
     */
    @PostConstruct
    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 引入id生成器
     */
    @Autowired
    private IdGenerateWorker worker;

    /**
     *
     */
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            // 调用方法，消费掉redis消息队列的任务
            while (true){
                try {
                    // 获取消息队列中的订单信息 key， field, value
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list =  redisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 判断订单信息是否为空
                    if (list == null || list.isEmpty()){
                        // 说明没有消息，跳过即可
                        continue;
                    }
                    // 解析数据，得到具体的数据信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 处理为秒杀订单对象
                    TbVoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new TbVoucherOrder(), true);
                    // 创建订单
                    createVoucherOrder(voucherOrder);
                    // 确认消息
                    redisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                }catch (Exception e){
                    log.info("处理订单异常 ", e);
                    handlePendingList();
                }finally {

                }
            }
        }

        /**
         * 处理pending list 中的任务
         */
        private void handlePendingList() {
            while (true){
                try{
                    // 获取 PendingList 中的订单信息 key， field, value
                    List<MapRecord<String, Object, Object>> list =  redisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 判断订单信息是否为空
                    if (list == null || list.isEmpty()){
                        // 说明没有消息，跳过即可
                        continue;
                    }
                    // 解析数据，得到具体的数据信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 处理为秒杀订单对象
                    TbVoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new TbVoucherOrder(), true);
                    // 创建订单
                    createVoucherOrder(voucherOrder);
                    // 确认消息XACk
                    // TODO 测试是否有 bug
                    redisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                }catch (Exception e){
                    log.error("处理未消费订单异常", e);
                }
            }
        }

        /**
         * 扩展（科普）一下啊什么是Pending List
         * pendingList 也是一种消息队列，是redis中Stream消息队列未收到消费确认信息（未被消费的信息）的存储集合
         * 被保存在此集合中的消息都是未被消费的，所以需要一个方案将消息消费（兜底方案）
         */
    }


    @Override
    public Result secKillVoucher(Long id) {
        // 判空
        if (id == null || id < 0){
            return Result.fail("id 不合法");
        }
        Long userId = 1L;
        long orderId = worker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                id.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 使用rabbitMQ发送消息
        TbVoucherOrder voucherOrder = new TbVoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(id);
        voucherOrder.setId(orderId);
        secKillRabbitMQService.sendMessage(voucherOrder);
        // 3.返回订单id
        return Result.ok(orderId);

    }

    /**
     * 创建秒杀优惠卷订单
     * @return
     */
    @Override
    public void createVoucherOrder(TbVoucherOrder voucherOrder){
        // 一人一单
        if (voucherOrder == null){
            log.info("订单为空");
            return;
        }
        // 先默认是1L
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 查询订单是否存在
        int count = query()
                .eq("voucher_id", voucherId)
                .eq("user_id", userId).count();
        if (count > 0){
            return;
        }
        // 扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!isSuccess){
            return;
        }
        // 保存订单信息
        save(voucherOrder);

    }

    /**
     * redisson 实现可重入锁，允许线程拿不到锁重试获取锁
     * 实现自动延期，即看门狗机制
     * @param id
     * @return
     */
//    @Override
//    public Result secKillVoucher(Long id) {
//        // 判空
//        if (id == null || id < 0){
//            return Result.fail("id 不合法");
//        }
//        // 尝试获取锁，获取不到则继续尝试
//        RLock lock = redissonClient.getLock("lock:order:" + 1L);
//        try{
//            boolean isLock = lock.tryLock(3L, -1, TimeUnit.SECONDS);
//
//            // 判断是否获取锁成功，继续操作
//            if (!isLock){
//                // TODO 等待尝试继续获取锁
//                return Result.fail("获取锁失败");
//            }
//            // 获取锁成功，继续操作
//            QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
//            wrapper.eq("voucher_id", id)
//                    .eq("user_id", 1L);
//            if (!list(wrapper).isEmpty()){
//                return Result.fail("不能重复下单");
//            }
//            // 查询优惠卷
//            TbSeckillVoucher voucher = seckillVoucherService.getById(id);
//            // 判断库存是否充足
//            if (voucher == null || voucher.getStock() < 1){
//                return Result.fail("库存不足");
//            }
//            int stock = voucher.getStock();
//            // 减库存
//            boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
//                    .eq("voucher_id", id) // where voucher_id = id
//                    .eq("stock", stock) // 版本号要一致
//                    .gt("stock", 0) // where stock > 0
//                    .update();
//            if (!isSuccess){
//                Result.fail("秒杀失败，库存不足");
//            }
//            // 生成订单信息
//            TbVoucherOrder voucherOrder = new TbVoucherOrder();
//            voucherOrder.setUserId(1L);
//            voucherOrder.setVoucherId(id);
//            // 使用订单生成算法工具类实现
//            voucherOrder.setId(worker.nextId("order:"));
//            // 保存订单
//            save(voucherOrder);
//            // 返回订单号
//            return Result.ok(voucherOrder.getId());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } finally {
//            lock.unlock();
//        }
//        // 判断锁存活时间
//        // 判断是否已经下单
//
//        // 判断库存
//
//        // 保存订单
//
//        // 释放锁
//        return Result.fail("秒杀失败");
//    }


//    @Override
//    public Result secKillVoucher(Long id) {
//        // 判空
//        if (id == null || id < 0){
//            return Result.fail("id 不合法");
//        }
//        lock = new SimpleRedisLock("secKill", redisTemplate);
//        // 尝试获取锁
//        try{
//            boolean isGetLockSuccess = lock.tryLock(2L);
//            // 判断是否获取锁成功，继续操作
//            if (!isGetLockSuccess){
//                // TODO 等待尝试继续获取锁
//                return Result.fail("获取锁失败");
//            }
//            // 获取锁成功，继续操作
//            QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
//            wrapper.eq("voucher_id", id)
//                    .eq("user_id", 1L);
//            if (!list(wrapper).isEmpty()){
//                return Result.fail("不能重复下单");
//            }
//            // 查询优惠卷
//            TbSeckillVoucher voucher = seckillVoucherService.getById(id);
//            // 判断库存是否充足
//            if (voucher == null || voucher.getStock() < 1){
//                return Result.fail("库存不足");
//            }
//            int stock = voucher.getStock();
//            // 减库存
//            boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
//                    .eq("voucher_id", id) // where voucher_id = id
//                    .eq("stock", stock) // 版本号要一致
//                    .gt("stock", 0) // where stock > 0
//                    .update();
//            if (!isSuccess){
//                Result.fail("秒杀失败，库存不足");
//            }
//            // 生成订单信息
//            TbVoucherOrder voucherOrder = new TbVoucherOrder();
//            voucherOrder.setUserId(1L);
//            voucherOrder.setVoucherId(id);
//            // 使用订单生成算法工具类实现
//            voucherOrder.setId(worker.nextId("order:"));
//            // 保存订单
//            save(voucherOrder);
//            // 返回订单号
//            return Result.ok(voucherOrder.getId());
//        }finally {
//            lock.unlock();
//        }
//
//    }


//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Result secKillVoucher(Long id) {
//        // 判空
//        if (id == null || id < 0){
//            return Result.fail("id 不合法");
//        }
//        // 尝试获取锁
//        String key = SECKILL_STOCK_KEY + id;
//        // TODO value应该是用户ID，在这里先写死
//        String value = "user_id_" + 1;
//        try{
//            // Boolean isTryLock = redisTemplate.opsForValue().setIfAbsent(key, value,SECKILL_STOCK_TTL, TimeUnit.SECONDS);
//            boolean isGetLockSuccess = lock.tryLock(2L);
//            // 判断是否获取锁成功，继续操作
//            if (!isGetLockSuccess){
//                // TODO 等待尝试继续获取锁
//                return Result.fail("获取锁失败");
//            }
//            // 获取锁成功，继续操作
//            QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
//            wrapper.eq("voucher_id", id)
//                    .eq("user_id", 1L);
//            if (!list(wrapper).isEmpty()){
//                return Result.fail("不能重复下单");
//            }
//            // 查询优惠卷
//            TbSeckillVoucher voucher = seckillVoucherService.getById(id);
//            // 判断库存是否充足
//            if (voucher == null || voucher.getStock() < 1){
//                return Result.fail("库存不足");
//            }
//            int stock = voucher.getStock();
//            // 减库存
//            boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
//                    .eq("voucher_id", id) // where voucher_id = id
//                    .eq("stock", stock) // 版本号要一致
//                    .gt("stock", 0) // where stock > 0
//                    .update();
//            if (!isSuccess){
//                Result.fail("秒杀失败，库存不足");
//            }
//            // 生成订单信息
//            TbVoucherOrder voucherOrder = new TbVoucherOrder();
//            voucherOrder.setUserId(1L);
//            voucherOrder.setVoucherId(id);
//            // 使用订单生成算法工具类实现
//            voucherOrder.setId(worker.nextId("order:"));
//            // 保存订单
//            save(voucherOrder);
//            // 返回订单号
//            return Result.ok(voucherOrder.getId());
//        }finally {
//
//            lock.unlock();
//
//            // 操作结束 释放锁
//            // 只能释放自己的锁
//            // 释放锁还得是原子操作，那么还得写lua脚本
//            // 执行lua脚本
////            Long result = (Long) redisTemplate.execute(
////                    SECKILL_SCRIPT,
////                    Arrays.asList(key),
////                    value
////            );
////            if (result == 1){
////                System.out.println("释放锁成功！");
////            }else{
////                System.out.println("释放锁失败！");
////            }
//
//        }
//
//    }



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




