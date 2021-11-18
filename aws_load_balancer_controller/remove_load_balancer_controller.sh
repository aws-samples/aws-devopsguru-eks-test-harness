#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm uninstall -n kube-system aws-load-balancer-controller

eksctl delete iamserviceaccount \
  --cluster=DevOpsGuruTestCluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller 

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws iam delete-policy --policy-arn arn:aws:iam::${AWS_ACCOUNT_ID}:policy/DevOpsGuruAWSLoadBalancerControllerIAMPolicy