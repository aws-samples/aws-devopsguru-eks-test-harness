#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

gradle wrapper
gradle build
docker build -t devopsguru/devopsguru-eks-test .

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(../get_region.sh)

aws ecr get-login-password \
  | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}".dkr.ecr."${REGION}".amazonaws.com

docker tag devopsguru/devopsguru-eks-test:latest "${AWS_ACCOUNT_ID}".dkr.ecr."${REGION}".amazonaws.com/devopsguru-eks-test:latest

docker push "${AWS_ACCOUNT_ID}".dkr.ecr."${REGION}".amazonaws.com/devopsguru-eks-test:latest
