#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

REGION=$(aws configure get region)
echo "Creating cluster in region $REGION"

if [ "$REGION" == "us-east-1" ]; then
  echo "WARNING: If you're getting an exception as you're running in IAD/us-east-1, you need to update the code for creating a cluster."
  echo "WARNING: Go to the cluster_bootstrap/create_cluster.sh file for details."
fi

# Create cluster
# If you're in IAD/us-east-1 and you're getting an exception,
# append `--region=us-east-1 --zones=us-east-1a,us-east-1b,us-east-1d` to the following command.
# Details https://eksctl.io/usage/creating-and-managing-clusters/

eksctl create cluster \
  --name DevOpsGuruTestCluster \
  --version 1.22 \
  --with-oidc \
  --managed=false \
  --nodegroup-name Group1 \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 4

# Create admin service account for further use
eksctl utils associate-iam-oidc-provider --cluster=DevOpsGuruTestCluster --approve
kubectl apply -f eks-admin-service-account.yaml

# Set up opentelemetry for container insights
ROLE_NAME=$(echo $(kubectl -n kube-system get cm -o yaml aws-auth | grep rolearn) | awk -F'role/' '{print $2}')
aws iam attach-role-policy \
  --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy \
  --role-name "$ROLE_NAME"

kubectl apply -f otel-container-insights-infra.yaml