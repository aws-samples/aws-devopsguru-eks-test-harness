#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Prometheus
POD_NAME=$(kubectl get pods \
  --namespace prometheus-server \
  -l "app.kubernetes.io/component=server,app.kubernetes.io/name=prometheus" \
  -o jsonpath="{.items[0].metadata.name}")
kubectl --namespace prometheus-server port-forward "$POD_NAME" 9090 &

# Chaos Dashboard
kubectl --namespace chaos-testing port-forward svc/chaos-dashboard 2333:2333 &

# Dashboard
kubectl proxy &
