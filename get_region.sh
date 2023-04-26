#!/bin/bash

region=$(aws configure get region)
if [ -z "$region" ]; then
  echo "Error: Region not defined in AWS configuration" >&2
  exit 1
else
  echo "$region"
fi
