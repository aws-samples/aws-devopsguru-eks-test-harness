# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
import requests


def enable_cpu_stress(base_url: str):
    result = requests.put("http://" + base_url + "/cpustress?enable=true")
    print(f"Request completed. Response code: {result.status_code}")


def disable_cpu_stress(base_url: str):
    result = requests.put("http://" + base_url + "/cpustress?enable=false")
    print(f"Request completed. Response code: {result.status_code}")
