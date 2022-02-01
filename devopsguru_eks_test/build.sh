#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

gradle wrapper

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
./gradlew bootBuildImage --imageName=devopsguru/devopsguru-eks-test

ECR_TOKEN=$(aws ecr get-login-password --region us-east-1)
docker login -u AWS -p ${ECR_TOKEN} https://${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com

docker tag devopsguru/devopsguru-eks-test:latest ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/devopsguru-eks-test:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/devopsguru-eks-test:latest