#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Source: https://aws.amazon.com/blogs/aws/amazon-guardduty-now-supports-amazon-eks-runtime-monitoring/

REGION=$(../get_region.sh)

VPC_ID=$(aws ec2 describe-vpcs | jq '.Vpcs[] | select(.Tags != null) | select(.Tags | map(select(.Key == "alpha.eksctl.io/cluster-name" and .Value == "DevOpsGuruTestCluster")) | length > 0) | .VpcId')
PRIVATE_SUBNETS=$(aws ec2 describe-subnets | jq -r '.Subnets[] | select(.VpcId == "vpc-0865dd17e1b1322c5") | select(.Tags[] | select(.Key == "Name" and (.Value | contains("Private")))) | .SubnetId')

# TODO create the security group + concatenate the subnet IDs

aws ec2 create-vpc-endpoint \
    --vpc-id "$VPC_ID" \
    --service-name com.amazonaws."$REGION".guardduty-data \
    --vpc-endpoint-type Interface \
    --subnet-ids $PRIVATE_SUBNETS \
    --security-group-ids <YourSecurityGroupId> \
    --private-dns-enabled

addonVersion=$(aws eks describe-addon-versions | jq -r '.addons[] | select(.addonName == "aws-guardduty-agent") | .addonVersions |= sort_by(.addonVersion) | .addonVersions[-1] | select(.compatibilities[].clusterVersion == "1.25") | .addonVersion')

echo "Installing GuardDuty agent version $addonVersion"

aws eks create-addon --cluster-name DevOpsGuruTestCluster \
  --addon-name aws-guardduty-agent \
  --addon-version "$addonVersion"

aws eks describe-addon --cluster-name DevOpsGuruTestCluster \
  --addon-name aws-guardduty-agent \
  --query addon.addonVersion \
  --output text
