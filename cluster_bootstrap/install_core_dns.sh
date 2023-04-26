#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Source: https://docs.aws.amazon.com/eks/latest/userguide/managing-coredns.html

CORE_DNS_VERSION=$(kubectl get deployment coredns --namespace kube-system -o json |
  jq -r '.spec.template.spec.containers[] | select(.name = "coredns") | .image | split(":") | last')

echo "Previously installed version of CoreDNS: $CORE_DNS_VERSION"

kubectl get deployment coredns -n kube-system -o yaml > aws-k8s-coredns-old.yaml

aws eks create-addon --cluster-name DevOpsGuruTestCluster \
  --addon-name coredns \
  --addon-version v1.9.3-eksbuild.2

aws eks describe-addon --cluster-name DevOpsGuruTestCluster \
  --addon-name coredns \
  --query addon.addonVersion \
  --output text
