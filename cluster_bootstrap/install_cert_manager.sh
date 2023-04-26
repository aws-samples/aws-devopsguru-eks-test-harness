#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Source: https://cert-manager.io/docs/installation/

curl -sL https://github.com/cert-manager/cert-manager/releases/download/v1.8.2/cert-manager.yaml | kubectl apply -f -

# Wait for cert manager to be ready
while true; do
    if kubectl get pod -n cert-manager | grep -q "NAME"; then
        break
    fi
    sleep 1
done

echo "cert-manager is ready"
