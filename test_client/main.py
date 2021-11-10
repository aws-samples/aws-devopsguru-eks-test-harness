# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
import failure_modes.alb_4xx_errors
import failure_modes.alb_5xx_errors
import failure_modes.node_failure
import failure_modes.cpu_stress_test
import failure_modes.pod_crash
import argparse
import subprocess
import json

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--trigger", required=True, help="Failure mode to trigger. "
                                                         "Valid values: alb_4xx, alb_5xx, "
                                                         "stop_instance, restart_instance,"
                                                         "enable_cpu_stress_test, disable_cpu_stress_test,"
                                                         "trigger_pod_crash")
    args = parser.parse_args()

    ingress_status = json.loads(subprocess.Popen("kubectl get ingress/devopsguru-eks-test-chart -o json", shell=True,
                                                 stdout=subprocess.PIPE).stdout.read())
    base_url = ingress_status["status"]["loadBalancer"]["ingress"][0]["hostname"]

    if args.trigger == "alb_4xx":
        failure_modes.alb_4xx_errors.trigger_4xx_error_count_anomaly(base_url)
    elif args.trigger == "alb_5xx":
        failure_modes.alb_5xx_errors.trigger_5xx_error_count_anomaly(base_url)
    elif args.trigger == "stop_instance":
        failure_modes.node_failure.stop_instances()
    elif args.trigger == "restart_instance":
        failure_modes.node_failure.restart_instances()
    elif args.trigger == "enable_cpu_stress_test":
        failure_modes.cpu_stress_test.enable_cpu_stress(base_url)
    elif args.trigger == "disable_cpu_stress_test":
        failure_modes.cpu_stress_test.disable_cpu_stress(base_url)
    elif args.trigger == "trigger_pod_crash":
        failure_modes.pod_crash.trigger_pod_crash(base_url)
    else:
        print(f"Failure mode trigger {args.trigger} not found")
