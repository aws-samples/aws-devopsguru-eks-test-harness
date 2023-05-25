# DevOps Guru EKS Test Harness

This project allows one to deploy an EKS cluster in their account and trigger various failure modes via a test client, in order to demonstrate functionality of DevOps Guru in a context of Kubernetes cluster.

## Requirements

In order to operate this test harness you will need the following:
* A PC with a unix-based opsystem (GNU/Linux or macOS) and a shell (bash, dash, zsh)
* Onboard used account to AWS DevOps Guru in one of the supported regions.
* [Gradle](https://gradle.org/install/)
* [Python 3.6+ with 'pip' utility](https://pip.pypa.io/en/stable/installation/)
* Docker
* [kubectl](https://kubernetes.io/docs/tasks/tools/)
* [eksctl](https://docs.aws.amazon.com/eks/latest/userguide/eksctl.html)
* [AWS CLI V2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) - only v2 is supported
* [Helm](https://helm.sh/docs/intro/install/)

## Installing the harness
In order to provision the cluster and install all the necessary elements:
* Authenticate into your AWS account using credentials that have mutating permissions.
```shell
aws configure
```

* Run the bootstrap script in the root folder of the repository.

```shell
./bootstrap.sh
```

### Inspecting the cluster
If you would like to inspect the content of deployed EKS cluster, start kubectl proxy via the script in the root of the repository
```shell
./start_proxy.sh
```

This will allow you to view:
* [Kubernetes dashboard](http://localhost:8001/api/v1/namespaces/default/services/https:kubernetes-dashboard:https/proxy/#/workloads?namespace=default)
* [Prometheus server web interface](http://localhost:9090/graph?g0.expr=&g0.tab=1&g0.stacked=0&g0.range_input=1h)

In order to stop the proxy process, run
```shell
./stop_proxy.sh
```

In order to get access token for Kubernetes dashboard, run
```shell
./get_dashboard_token.sh
```

## Running tests

Before running tests, please make sure that your cluster has been running for at least 60 minutes, to give DevOps Guru a chance to ingest and index all the metrics.

In order to run test cases, make sure you have Python 3.6+ interpreter installed and run:
```shell
./run_test.sh <test_name>
```

Currently supported tests scenarios:
* __alb_4xx__ - triggers a series of 4XX errors in test API, producing _ApplicationELB HTTPCode_Target_4XX_Count Anomalous_ insights in DevOps Guru. Please keep in mind, that this can take up to 15-20 minutes to trigger.
* __alb_5xx__ triggers a series of 5XX errors in test API, producing _ApplicationELB HTTPCode_Target_5XX_Count Anomalous_ insights in DevOps Guru. Please keep in mind, that this can take up to 15-20 minutes to trigger.
* __stop_instance__ - stops one of underlying EC2 instances in EKS node group, producing _ContainerInsights cluster_failed_node_count Anomalous In Stack eksctl-DevOpsGuruTestCluster-cluster_ insight in DevOps Guru.
* __restart_instance__ - restarts all the underlying EC2 instances in EKS node group, ending the anomaly caused by __stop_instance__.
* __enable_cpu_stress_test__ - enables CPU stress test mode, which brings overall cluster CPU utilization to above 90%. After 30 minutes, this produces an anomaly, which does not produce a separate insight, but will be shown as a part of __alb_5xx__, __alb_4xx__ and __stop_instance__ insights. Before enabling this mode, make sure that the cluster has been running for at least 60 minutes to establish baseline for utilization.
* __disable_cpu_stress_test__ - disables CPU stress test mode mentioned in __enable_cpu_stress_test__
* __trigger_pod_crash__ - installs a misconfigured deployment that induces a rolling pod crash due to a failing probe to demonstrate __pod_number_of_container_restarts__ insights
* __disable_pod_crash__ - restores normal deployment configuration after __trigger_pod_crash__

Anomalous metric values can be confirmed via CloudWatch console, and DevOps Guru produced anomalies can be seen in DevOps Guru console.

## Cleaning up test resources

In order to clean up test harness resources from your account you can run:
```
./cleanup.sh
```
In case the cleanup script fails, you can attempt manual deletion of CloudFormation stack names __eksctl-DevOpsGuruTestCluster-cluster__.
