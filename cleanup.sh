#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

export AWS_PAGER=""

echo "Remove test deployment"
(cd devopsguru_eks_test && sh uninstall_chart.sh)

echo "Waiting 5 minutes for resources to be removed..."
sleep 300

echo "Cleanup ECR repo"
(cd ecr && sh remove_repo.sh)

echo "Remove load balancer controller"
(cd aws_load_balancer_controller && sh remove_load_balancer_controller.sh)

echo "Cleanup cluster"
(cd cluster_bootstrap && sh remove_cluster.sh)
