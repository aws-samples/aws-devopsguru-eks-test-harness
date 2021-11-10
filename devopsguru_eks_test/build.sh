#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
./gradlew bootBuildImage --imageName=devopsguru/devopsguru-eks-test
aws ecr get-login --region us-east-1 --no-include-email | sh
docker tag devopsguru/devopsguru-eks-test:latest ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/devopsguru-eks-test:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/devopsguru-eks-test:latest