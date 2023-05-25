#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

aws eks get-token --cluster-name DevOpsGuruTestCluster | jq -r '.status.token'
