# DevOps Guru EKS Test Harness

This project allows one to deploy an EKS cluster in their account and trigger various failure modes via a test client, in order to demonstrate functionality of DevOps Guru in a context of Kubernetes cluster.

## Requirements

In order to operate this test harness you will need the following:
* A PC with a unix-based opsystem (GNU/Linux or MacOS) and a shell (bash, dash, zsh)
* OpenJDK 11
* Python 3.6+ with 'pip' utility (https://pip.pypa.io/en/stable/installation/)
* Docker
* kubectl utility (https://kubernetes.io/docs/tasks/tools/)
* eksctl utility (https://docs.aws.amazon.com/eks/latest/userguide/eksctl.html)
* aws cli utility (https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
* helm utility (https://helm.sh/docs/intro/install/)
* Onboard used account to DevOps Guru in us-east-1

## Installing the harness
In order to provision the cluster and install all the necessary elements:
* Authenticate into your aws account using credentials that have mutating permissions and set default region to us-east-1
 ```
 aws configure
 ```

* Run the bootstrap script in the root folder of the repository.

```
./bootstrap.sh
```

### Inspecting the cluster
If you would like to inspect the content of deployed EKS cluster, start kubectl proxy via script in the root of repository
```
./start_proxy.sh
```
This will allow you to view:
* Kubernetes dashboard (http://localhost:8001/api/v1/namespaces/default/services/https:kubernetes-dashboard:https/proxy/#/workloads?namespace=default)
* Prometheus server web interface (http://localhost:9090/graph?g0.expr=&g0.tab=1&g0.stacked=0&g0.range_input=1h)

In order to stop proxy process simply run
```
./stop_proxy.sh
```

In order to get access token for Kubernetes dashboard run
```
./get_dashboard_token.sh
```

## Running tests

Before running tests, please make sure that your cluster has been running for at least 60 minutes, to give DevOps Guru a chance to ingest and index all the metrics.

In order to run test cases, make sure you have python 3.6+ interpreter installed and run:
```
cd test_client
./run_test.sh <test_name>
```

Currently supported tests scenarios:
* __alb_4xx__ - triggers a series of 4XX errors in test API, producing _ApplicationELB HTTPCode_Target_4XX_Count Anomalous_ insights in DevOps Guru. Please keep in mind, that this can take up to 15-20 minutes to trigger.
* __alb_5xx__ triggers a series of 5XX errors in test API, producing _ApplicationELB HTTPCode_Target_5XX_Count Anomalous_ insights in DevOps Guru. Please keep in mind, that this can take up to 15-20 minutes to trigger.
* __stop_instance__ - stops one of underlying EC2 instances in EKS nodegroup, producing _ContainerInsights cluster_failed_node_count Anomalous In Stack eksctl-DevOpsGuruTestCluster-cluster_ insight in DevOps Guru.
* __restart_instance__ - restarts all the underlying EC2 instances in EKS node-group, ending the anomaly caused by __stop_instance__.
* __enable_cpu_stress_test__ - enables cpu stress test mode, which brings overall cluster cpu utilization to above 90%. After 30 minutes, this produces an anomaly, which does not produce a separate insight, but will be shown as a part of __alb_5xx__, __alb_4xx__ and __stop_instance__ insights. Before enabling this mode, make sure that the cluster has been running for at least 60 minutes to establish baseline for utilization.
* __disable_cpu_stress_test__ - disables cpu stress test mode mentioned in __enable_cpu_stress_test__
* __trigger_pod_crash__ - triggers pod crash to demonstrate __pod_number_of_container_restarts__ insights

Anomalous metric values can be confirmed via CloudWatch console, and DevOps Guru produced anomalies can be seen in DevOps Guru console.