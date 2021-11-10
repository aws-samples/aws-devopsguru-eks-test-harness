// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.capstone.devopsguru_eks_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevopsguruEksTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevopsguruEksTestApplication.class, args);
	}

}
