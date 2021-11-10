#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

aws ecr get-login --region us-east-1 | eval
aws ecr create-repository --repository-name devopsguru-eks-test --image-scanning-configuration scanOnPush=true --region us-east-1