#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
helm install --set image.repository=${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/devopsguru-eks-test devopsguru-eks-test chart_misconfigured