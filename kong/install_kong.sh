#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm repo add kong https://charts.konghq.com
helm repo update

kubectl create namespace kong
helm install kong-ingress kong/kong --namespace kong --set ingressController.installCRDs=false --values values.yaml
kubectl apply -f kong_prometheus.yaml
