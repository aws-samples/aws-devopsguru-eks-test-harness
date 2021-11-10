#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm repo add kubernetes-dashboard https://kubernetes.github.io/dashboard/
helm repo update
helm install kube-dashboard kubernetes-dashboard/kubernetes-dashboard -f values.yaml
