#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(../get_region.sh)

aws ecr get-login-password \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID".dkr.ecr."${REGION}".amazonaws.com

aws ecr create-repository --repository-name devopsguru-eks-test --image-scanning-configuration scanOnPush=true
