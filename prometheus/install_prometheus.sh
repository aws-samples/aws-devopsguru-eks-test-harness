#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

REGION=$(../get_region.sh)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text --region "$REGION")
echo "Installing Amazon Managed Prometheus in $REGION"
WORKSPACE_ID=$(aws amp create-workspace --alias DevOpsGuruPrometheus | jq -r '.workspaceId')
echo "Prometheus workspace ID: $WORKSPACE_ID"

sleep 60

PROMETHEUS_ENDPOINT=$(aws amp describe-workspace --workspace-id "$WORKSPACE_ID" | jq -r '.workspace.prometheusEndpoint')
echo "Prometheus endpoint: $PROMETHEUS_ENDPOINT"

sed -e "s#<<REGION>>#$REGION#g" \
  -e "s#<<PROMETHEUS_ENDPOINT>>#$PROMETHEUS_ENDPOINT#g" \
  -e "s#<<AWS_ACCOUNT_ID>>#$AWS_ACCOUNT_ID#g" \
  prometheus_values_template.yaml > prometheus_values.yaml

./set_up_prometheus_role.sh

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add kube-state-metrics https://kubernetes.github.io/kube-state-metrics
helm repo update

kubectl apply -f ./prometheus-namespace.yaml

helm upgrade --install prometheus prometheus-community/prometheus \
  -n prometheus-server \
  -f prometheus_values.yaml
