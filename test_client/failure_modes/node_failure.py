# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
import boto3

def stop_instances():
    client = boto3.client('ec2')

    response = client.describe_instances(Filters=[
        {
            'Name': 'tag:kubernetes.io/cluster/DevOpsGuruTestCluster',
            'Values': ['owned']
        },
    ])

    for reservation in response['Reservations']:
        if reservation['Instances'][0]['State']['Name'] == 'running':
            instance_id = reservation['Instances'][0]['InstanceId']
            ec2_resources = boto3.resource('ec2')
            instance = ec2_resources.Instance(instance_id)
            print(f"Stopping {instance_id}")
            instance.stop()
            break


def restart_instances():
    client = boto3.client('ec2')

    response = client.describe_instances(Filters=[
        {
            'Name': 'tag:kubernetes.io/cluster/DevOpsGuruTestCluster',
            'Values': ['owned']
        },
    ])
    ec2_resources = boto3.resource('ec2')
    for instance in response['Reservations'][0]['Instances']:
        if instance['State']['Name'] == 'stopped':
            instance_id = instance['InstanceId']
            instance_resource = ec2_resources.Instance(instance_id)
            print(f'Restarting instance {instance_id}')
            instance_resource.start()
