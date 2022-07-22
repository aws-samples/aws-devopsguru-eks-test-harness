// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.devopsguru.eks.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class DevopsguruEksTestConfiguration {

    @Bean()
    JedisPool redisConnectionPool() {
        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxTotal(3);
        return new JedisPool(poolCfg, "redis-cluster-master", 6379, 500, "password", false);
    }

}
