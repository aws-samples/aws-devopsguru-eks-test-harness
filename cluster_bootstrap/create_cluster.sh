#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Create cluster
eksctl create cluster \
  --name DevOpsGuruTestCluster \
  --version 1.21 \
  --with-oidc \
  --nodegroup-name Group1 \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 4 \
  --zones us-east-1a,us-east-1b,us-east-1c

# Create admin service account for further use
kubectl apply -f eks-admin-service-account.yaml

# Set up opentelemetry for container insights
ROLE_NAME=$(echo $(kubectl -n kube-system get cm -o yaml aws-auth | grep rolearn) | awk -F'role/' '{print $2}')
aws iam attach-role-policy \
  --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy \
  --role-name "$ROLE_NAME"

kubectl apply -f otel-container-insights-infra.yaml
