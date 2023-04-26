#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

REGION=$(../get_region.sh)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
CLUSTER=DevOpsGuruTestCluster
echo "Creating cluster in region $REGION"

if [ "$REGION" = "us-east-1" ]; then
  echo "WARNING: If you're getting an exception as you're running in IAD/us-east-1, you need to update the code for creating a cluster."
  echo "WARNING: Go to the cluster_bootstrap/create_cluster.sh file for details."
fi

# Create cluster
# If you're in IAD/us-east-1 and you're getting an exception,
# append `--region=us-east-1 --zones=us-east-1a,us-east-1b,us-east-1d` to the following command.
# Details https://eksctl.io/usage/creating-and-managing-clusters/

# Node type is based on the default from https://eksctl.io/
eksctl create cluster \
  --name $CLUSTER \
  --version 1.26 \
  --with-oidc \
  --managed=true \
  --nodegroup-name t3-xlarge \
  --node-type t3.xlarge \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 6 \
  --region "$REGION"

# Enable control plane logging
aws eks update-cluster-config \
    --region "$REGION" \
    --name $CLUSTER \
    --logging '{"clusterLogging":[{"types":["api","audit","authenticator","controllerManager","scheduler"],"enabled":true}]}'

# Create admin service account for further use
eksctl utils associate-iam-oidc-provider --cluster=$CLUSTER --approve
kubectl apply -f eks-admin-service-account.yaml

# EBS CSI Driver
EBS_CSI_ROLE=AmazonEKS_EBS_CSI_DriverRole_$REGION
eksctl create iamserviceaccount \
  --name ebs-csi-controller-sa \
  --namespace kube-system \
  --cluster $CLUSTER \
  --attach-policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy \
  --approve \
  --role-only \
  --role-name "$EBS_CSI_ROLE"

aws eks create-addon --cluster-name $CLUSTER \
  --addon-name aws-ebs-csi-driver \
  --service-account-role-arn arn:aws:iam::"$AWS_ACCOUNT_ID":role/"$EBS_CSI_ROLE"

# See:
# - https://docs.aws.amazon.com/eks/latest/userguide/opentelemetry.html
# - https://aws-otel.github.io/docs/getting-started/adot-eks-add-on/add-on-configuration-collector-deployment
# - https://aws-otel.github.io/docs/getting-started/adot-eks-add-on/config-intro

./install_core_dns.sh
./install_cert_manager.sh

eksctl create iamserviceaccount \
    --name aws-otel-collector \
    --namespace default \
    --cluster $CLUSTER \
    --attach-policy-arn arn:aws:iam::aws:policy/AmazonPrometheusRemoteWriteAccess \
    --attach-policy-arn arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess \
    --attach-policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy \
    --approve \
    --override-existing-serviceaccounts

kubectl apply -f adot-namespace.yaml
eksctl create iamserviceaccount \
    --name aws-otel-sa \
    --namespace aws-otel-eks \
    --cluster $CLUSTER \
    --attach-policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy \
    --approve \
    --override-existing-serviceaccounts

aws eks create-addon --addon-name adot \
  --cluster-name $CLUSTER \
  --configuration-values file://adot-collector-configuration.json

aws eks describe-addon --addon-name adot --cluster-name $CLUSTER

# Create an ADOT collector with CloudWatch exporter
kubectl apply -f adot-collector-cloudwatch-insights.yaml
sed "s/<<REGION>>/$REGION/g" adot-collector-cloudwatch-prometheus-metrics.yaml | kubectl apply -f -

# ADOT collector without using ADOt operator since it is broken right now
kubectl apply -f otel-daemonset-collector-cloudwatch-insights.yaml

# Install Metrics Server
./install_metrics_server.sh

# Enable ingestion of container logs into CloudWatch
./enable_cloudwatch_container_logs.sh
