package com.xiaohu.heimadianping.controller;

import com.xiaohu.heimadianping.common.Result;
import com.xiaohu.heimadianping.service.TbVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author xiaohu
 * @date 2022/11/02/ 21:11
 * @description 优惠卷订单控制层接口
 */
@RestController
@RequestMapping("/voucher")
public class VoucherOrderController {
    /**
     * 引入service
     */
    @Autowired
    private TbVoucherOrderService voucherOrderService;

    /**
     *
     * @param id
     * @return
     */
    @GetMapping("/secKill/{id}")
    public Result secKillVoucher(@PathVariable("id") Long id){
        return  voucherOrderService.secKillVoucher(id);
    }

}
