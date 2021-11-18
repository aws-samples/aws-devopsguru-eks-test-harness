#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#Remove test deployment
cd devopsguru_eks_test
sh uninstall_chart.sh
cd ..

sleep 300

#Cleanup ECR repo
cd ecr
sh remove_repo.sh
cd ..

#Remove load balancer controller 
cd aws_load_balancer_controller
sh remove_load_balancer_controller.sh
cd ..

#Cleanup cluster
cd cluster_bootstrap
sh remove_cluster.sh
cd ..

