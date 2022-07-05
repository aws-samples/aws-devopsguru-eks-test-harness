#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(aws configure get region)

helm upgrade --set image.repository="${AWS_ACCOUNT_ID}".dkr.ecr."${REGINO}".amazonaws.com/devopsguru-eks-test devopsguru-eks-test chart