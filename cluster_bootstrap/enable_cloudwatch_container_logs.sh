# https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-setup-logs-FluentBit.html

kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/cloudwatch-namespace.yaml

ClusterName=DevOpsGuruTestCluster
RegionName=$(../get_region.sh)
FluentBitHttpPort='2020'
FluentBitReadFromHead='Off'

[[ ${FluentBitReadFromHead} = 'On' ]] && FluentBitReadFromTail='Off' || FluentBitReadFromTail='On'
[[ -z ${FluentBitHttpPort} ]] && FluentBitHttpServer='Off' || FluentBitHttpServer='On'

kubectl create configmap fluent-bit-cluster-info \
  --from-literal=cluster.name=${ClusterName} \
  --from-literal=http.server=${FluentBitHttpServer} \
  --from-literal=http.port=${FluentBitHttpPort} \
  --from-literal=read.head=${FluentBitReadFromHead} \
  --from-literal=read.tail=${FluentBitReadFromTail} \
  --from-literal=logs.region=${RegionName} -n amazon-cloudwatch

kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/fluent-bit/fluent-bit.yaml

kubectl get pods -n amazon-cloudwatch
