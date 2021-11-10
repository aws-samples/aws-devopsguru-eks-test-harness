// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.capstone.devopsguru_eks_test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

@RestController
public class DevopsguruEksTestController {

    @Autowired
    JedisPool redisPool;

    @GetMapping("/")
    public String index() {

        return "Greetings from Spring Boot!";
    }

    @GetMapping("/forbidden")
    public void forbidden() {
        throw new ForbiddenException();
    }

    @GetMapping("/badgateway")
    public void badGateway() {
        throw new GatewayException();
    }

    @PutMapping("/cpustress")
    public void enableCpuStressTest(@RequestParam boolean enable) {
        try (Jedis client = redisPool.getResource()) {
            client.set("cpustresstest", enable ? "true" : "false");
        }
    }

    @PutMapping("/memorystress")
    public void enableMStressTest(@RequestParam boolean enable) {
        try (Jedis client = redisPool.getResource()) {
            client.set("memorystresstest", enable ? "true" : "false");
        }
    }

    @PutMapping("/crash")
    public void crash() throws Exception{
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe object = (Unsafe) theUnsafe.get(null);
        object.getByte(0);
    }
}
