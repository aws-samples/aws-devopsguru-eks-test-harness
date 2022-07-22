// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.devopsguru.eks.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.ByteBuffer;
import java.util.Optional;

@Component
public class MemoryStressBackgroundComponent {
    @Autowired
    JedisPool redisPool;

    ByteBuffer buffer;
    boolean stressTestRunning = false;

    static final int BUFFER_SIZE = 256 * 1024 * 1024;

    @Scheduled(fixedRate = 30000)
    public void checkMemoryStressRequest() {
        boolean stressTestEnabled;
        try (Jedis client = redisPool.getResource()) {
            stressTestEnabled = Optional.ofNullable(client.get("memorystresstest"))
                    .orElse("false").equals("true");
        }
        if (stressTestEnabled && !stressTestRunning){
            buffer = ByteBuffer.allocate(BUFFER_SIZE);
        }
        else if (!stressTestEnabled && stressTestRunning) {
            buffer = null;
        }
    }
}
