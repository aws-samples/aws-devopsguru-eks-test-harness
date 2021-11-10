#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm install redis-cluster bitnami/redis\
  --set cluster.slaveCount=1 \
  --set auth.password=password \
  --set securityContext.enabled=true \
  --set securityContext.fsGroup=2000 \
  --set securityContext.runAsUser=1000 \
  --set volumePermissions.enabled=false \
  --set master.persistence.enabled=false \
  --set slave.persistence.enabled=false \
