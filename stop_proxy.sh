#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

pgrep -f 'kubectl proxy' | xargs kill -9
pgrep -f 'kubectl --namespace prometheus-server port-forward' | xargs kill -9
pgrep -f 'kubectl --namespace chaos-testing port-forward' | xargs kill -9
