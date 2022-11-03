package com.xiaohu.heimadianping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohu.heimadianping.domain.TbSeckillVoucher;
import com.xiaohu.heimadianping.service.TbSeckillVoucherService;
import com.xiaohu.heimadianping.mapper.TbSeckillVoucherMapper;
import org.springframework.stereotype.Service;

/**
* @author xiaohu
* @description 针对表【tb_seckill_voucher(秒杀优惠券表，与优惠券是一对一关系)】的数据库操作Service实现
* @createDate 2022-11-02 21:33:10
*/
@Service
public class TbSeckillVoucherServiceImpl extends ServiceImpl<TbSeckillVoucherMapper, TbSeckillVoucher>
    implements TbSeckillVoucherService{

}




