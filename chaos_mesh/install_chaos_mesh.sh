#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm repo add chaos-mesh https://charts.chaos-mesh.org

kubectl create namespace chaos-testing

helm install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace=chaos-testing \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
  --version 2.5.2

kubectl apply -f rbac.yaml

kubectl create token account-cluster-manager-tynbs
