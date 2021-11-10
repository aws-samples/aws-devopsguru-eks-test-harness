// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.capstone.devopsguru_eks_test;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CpuStressBackgroundComponent {
    @Autowired
    JedisPool redisPool;

    private ExecutorService executorService;
    private AtomicBoolean stressTestRunning = new AtomicBoolean(false);
    private static double loadTestPercentage = 0.95;
    private static double baselineLoadPercentage = 0.3;

    @PostConstruct
    public void init() {

        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("cpu-stress-%d").build();

        executorService = Executors.newSingleThreadExecutor(factory);
        executorService.execute(() -> {
            while (true) {
                if (System.currentTimeMillis() % 1000 == 0) {
                    try {
                        double loadPercentage = stressTestRunning.get() ? loadTestPercentage : baselineLoadPercentage;
                        Thread.sleep((long) Math.floor((1 - loadPercentage) * 1000));
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    @Scheduled(fixedRate = 5000)
    public void checkCpuStressRequest() {
        boolean stressTestEnabled;
        try (Jedis client = redisPool.getResource()) {
            stressTestEnabled = Optional.ofNullable(client.get("cpustresstest"))
                    .orElse("false").equals("true");
        }
        stressTestRunning.set(stressTestEnabled);
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
