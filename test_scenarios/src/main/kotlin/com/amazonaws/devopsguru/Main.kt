@file:JvmName("Main")

package com.amazonaws.devopsguru

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.createNetworkAcl
import aws.sdk.kotlin.services.ec2.deleteNetworkAcl
import aws.sdk.kotlin.services.ec2.describeNetworkAcls
import aws.sdk.kotlin.services.ec2.describeRouteTables
import aws.sdk.kotlin.services.ec2.describeSubnets
import aws.sdk.kotlin.services.ec2.model.DescribeRouteTablesRequest
import aws.sdk.kotlin.services.ec2.model.Filter
import aws.sdk.kotlin.services.ec2.model.NetworkAclAssociation
import aws.sdk.kotlin.services.ec2.model.ResourceType
import aws.sdk.kotlin.services.ec2.model.Subnet
import aws.sdk.kotlin.services.ec2.model.Tag
import aws.sdk.kotlin.services.ec2.model.TagSpecification
import aws.sdk.kotlin.services.ec2.replaceNetworkAclAssociation
import aws.sdk.kotlin.services.eks.EksClient
import aws.sdk.kotlin.services.eks.describeCluster
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.terminal.ExperimentalTerminalApi
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger

fun main(args: Array<String>) {
    MakeNodeUnhealthy().subcommands(DisableControlPlaneCommunicationInAz()).main(args)
}

class MakeNodeUnhealthy : CliktCommand() {
    override fun run() = Unit
}

class DisableControlPlaneCommunicationInAz : CliktCommand() {
    val clusterName by option("-c", "--cluster-name", help = "Names of the EKS cluster").required()
    val region by option("--region", "-r", help = "AWS region")
    val outageDuration by
        option(
                "--outage-duration",
                "-d",
                help = "The duration in seconds for which the private subnet will be inaccessible"
            )
            .convert { it.toInt().seconds }
            .default(2.minutes)

    @OptIn(ExperimentalTerminalApi::class) private val t = Terminal()

    override fun run() = runBlocking {
        EksClient.fromEnvironment { region = this@DisableControlPlaneCommunicationInAz.region }
            .use { eksClient ->
                Ec2Client.fromEnvironment {
                        region = this@DisableControlPlaneCommunicationInAz.region
                    }
                    .use { ec2Client -> triggerScenario(eksClient, ec2Client) }
            }
    }

    private suspend fun triggerScenario(eksClient: EksClient, ec2Client: Ec2Client) {
        val cluster = requireNotNull(eksClient.describeCluster { name = clusterName }.cluster)

        val vpcId = cluster.resourcesVpcConfig?.vpcId
        val clusterSubnets = cluster.resourcesVpcConfig?.subnetIds

        val privateSubnet =
            ec2Client
                .describeSubnets { subnetIds = clusterSubnets }
                .subnets
                ?.firstOrNull { isSubnetPrivate(it, ec2Client) }
        requireNotNull(privateSubnet)

        val networkAclWithDenyAll =
            ec2Client.createNetworkAcl {
                this.vpcId = vpcId
                tagSpecifications =
                    listOf(
                        TagSpecification {
                            resourceType = ResourceType.NetworkAcl
                            tags =
                                listOf(
                                    Tag {
                                        key = "Name"
                                        value = "deny-all-test-nacl"
                                    }
                                )
                        }
                    )
            }

        val originalNetworkAclAssociation =
            getCurrentNetworkAclAssociationForSubnet(privateSubnet, ec2Client)
        try {
            logger.info {
                "Disabling subnet ${privateSubnet.name()} (${privateSubnet.subnetId}) used by $clusterName " +
                    "by attaching NACL ${networkAclWithDenyAll.networkAcl?.networkAclId}"
            }
            ec2Client.replaceNetworkAclAssociation {
                associationId = originalNetworkAclAssociation.networkAclAssociationId
                networkAclId = networkAclWithDenyAll.networkAcl?.networkAclId
            }

            logger.info {
                "Now, the nodes on the selected subnet should be in NotReady state for $outageDuration"
            }
            delay(outageDuration)
        } finally {
            // Switch back to the original Network ACL
            logger.info { "Scenario cleanup, removing the NACL and enabling the AZ" }
            val newNetworkAclAssociation =
                getCurrentNetworkAclAssociationForSubnet(privateSubnet, ec2Client)
            ec2Client.replaceNetworkAclAssociation {
                associationId = newNetworkAclAssociation.networkAclAssociationId
                networkAclId = originalNetworkAclAssociation.networkAclId
            }

            ec2Client.deleteNetworkAcl {
                networkAclId = networkAclWithDenyAll.networkAcl?.networkAclId
            }
        }
    }

    private suspend fun getCurrentNetworkAclAssociationForSubnet(
        subnet: Subnet,
        ec2Client: Ec2Client
    ): NetworkAclAssociation {
        val associatedAcl =
            ec2Client
                .describeNetworkAcls {
                    filters =
                        listOf(
                            Filter {
                                name = "association.subnet-id"
                                values = listOf(subnet.subnetId!!)
                            }
                        )
                }
                .networkAcls
                ?.firstOrNull()

        val networkAclAssociation =
            associatedAcl?.associations?.firstOrNull { it.subnetId == subnet.subnetId }
        return requireNotNull(networkAclAssociation)
    }

    private suspend fun isSubnetPrivate(subnet: Subnet, ec2Client: Ec2Client): Boolean {
        val routeTablesRequest = DescribeRouteTablesRequest {
            filters =
                listOf(
                    Filter {
                        name = "association.subnet-id"
                        values = listOf(subnet.subnetId!!)
                    }
                )
        }
        var routeTablesResult = ec2Client.describeRouteTables(routeTablesRequest)
        if (routeTablesResult.routeTables.isNullOrEmpty()) {
            // check the main route table here
            routeTablesResult =
                ec2Client.describeRouteTables {
                    filters =
                        listOf(
                            Filter {
                                name = "association.main"
                                values = listOf("true")
                            },
                            Filter {
                                name = "vpc-id"
                                values = listOf(subnet.vpcId!!)
                            }
                        )
                }
        }
        return (routeTablesResult.routeTables ?: emptyList()).none { routeTable ->
            val routes = routeTable.routes
            // Route table with no routes should not be OK
            routes.isNullOrEmpty() ||
                routes.any { route -> route.gatewayId?.startsWith("igw-") == true }
        }
    }

    private fun Subnet.name() = tags?.firstOrNull { it.key == "Name" }?.value

    companion object {
        private val logger = logger()
    }
}
