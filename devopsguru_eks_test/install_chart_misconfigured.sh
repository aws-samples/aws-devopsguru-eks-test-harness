#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(../get_region.sh)

helm install --set image.repository="${AWS_ACCOUNT_ID}".dkr.ecr."${REGION}".amazonaws.com/devopsguru-eks-test devopsguru-eks-test chart_misconfigured
