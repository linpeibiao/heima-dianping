package com.xiaohu.heimadianping.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author xiaohu
 * @date 2022/11/03/ 0:46
 * @description
 */
@Configuration
@MapperScan("com.xiaohu.heimadianping.mapper")
public class MybatisPlusConfig {
}
