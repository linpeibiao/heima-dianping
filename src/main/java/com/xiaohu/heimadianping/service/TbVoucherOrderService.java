package com.xiaohu.heimadianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohu.heimadianping.common.Result;
import com.xiaohu.heimadianping.domain.TbVoucherOrder;

/**
* @author xiaohu
* @description 针对表【tb_voucher_order】的数据库操作Service
* @createDate 2022-11-02 21:04:16
*/
public interface TbVoucherOrderService extends IService<TbVoucherOrder> {

    /**
     * 秒杀优惠卷
     * @param id
     * @return
     */
    Result secKillVoucher(Long id);

    /**
     * 创建保存订单
     * @param voucherOrder
     * @return
     */
    void createVoucherOrder(TbVoucherOrder voucherOrder);
}
