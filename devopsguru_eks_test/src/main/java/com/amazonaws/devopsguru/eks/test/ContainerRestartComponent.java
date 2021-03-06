package com.amazonaws.devopsguru.eks.test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Random;

@Component
public class ContainerRestartComponent {

    long startTime;
    boolean rebootActive;
    long rebootDelay;

    @PostConstruct
    public void init() {

        rebootActive = Optional.ofNullable(System.getenv("REBOOT_LOOP"))
                .orElse("false").equals("true");
        startTime = System.currentTimeMillis();
        rebootDelay =  Math.abs(new Random().nextInt()) % 300000;
    }

    @Scheduled(fixedRate = 1000)
    public void loop() {
        if (rebootActive && (System.currentTimeMillis() - startTime > rebootDelay)) {
            System.exit(-1);
        }
    }
}
