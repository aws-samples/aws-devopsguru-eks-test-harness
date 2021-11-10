#!/bin/sh

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

ps axf | grep 'kubectl proxy' | grep -v grep | awk '{print "kill -9 " $1}' | sh
ps axf | grep 'kubectl --namespace default port-forward' | grep -v grep | awk '{print "kill -9 " $1}' | sh