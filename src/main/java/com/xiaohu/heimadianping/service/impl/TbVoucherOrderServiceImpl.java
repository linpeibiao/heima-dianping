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
import org.springframework.stereotype.Service;

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

    /**
     * 引入id生成器
     */
    @Autowired
    private IdGenerateWorker worker;

    @Override
    public Result secKillVoucher(Long id) {
        // 判空
        if (id == null || id < 0){
            return Result.fail("id 不合法");
        }
        // 获取当前登录用户
        // 判断用户是否已经下单
        QueryWrapper<TbVoucherOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("voucher_id", id)
                .eq("user_id", 1L);
        if (getOne(wrapper) != null){
            return Result.fail("不能重复下单");
        }
        // 查询优惠卷
        TbSeckillVoucher voucher = seckillVoucherService.getById(id);
        // 判断库存是否充足
        if (voucher == null || voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        // 减库存
        boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", id) // where voucher_id = id
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
    }
}




