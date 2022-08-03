#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

helm repo add chaos-mesh https://charts.chaos-mesh.org

kubectl create namespace chaos-testing

helm install chaos-mesh chaos-mesh/chaos-mesh --namespace chaos-testing
