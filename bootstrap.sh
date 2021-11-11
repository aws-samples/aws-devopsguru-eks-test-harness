#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#Bootstrap initial cluster and node group
cd cluster_bootstrap
sh create_cluster.sh
cd ..

#Install kube dashboard
cd kubernetes_dashboard
sh install_dashboard.sh
cd ..

#Install ChaosMesh
cd chaos_mesh
sh install_chaos_mesh.sh
cd ..

#Install prometheus
cd prometheus
sh install_prometheus.sh
cd ..

#Install Kong
cd kong
sh install_kong.sh
cd ..

#Add ALB controller
cd aws_load_balancer_controller
sh create_load_balancer_controller.sh
cd ..

#Create ECR repo
cd ecr
sh create_repo.sh
cd ..

#Install Redis
cd redis
sh install_redis.sh
cd ..

#Build and install test deployment
cd devopsguru_eks_test
sh build.sh
sh install_chart.sh
cd ..

#Install test client requirements
cd test_client
pip install -r requirements.txt
cd ..