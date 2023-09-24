@file:JvmName("Main")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.amazonaws.devopsguru

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.createNetworkAcl
import aws.sdk.kotlin.services.ec2.deleteNetworkAcl
import aws.sdk.kotlin.services.ec2.describeNetworkAcls
import aws.sdk.kotlin.services.ec2.describeSubnets
import aws.sdk.kotlin.services.ec2.model.Filter
import aws.sdk.kotlin.services.ec2.model.NetworkAclAssociation
import aws.sdk.kotlin.services.ec2.model.ResourceType
import aws.sdk.kotlin.services.ec2.model.Subnet
import aws.sdk.kotlin.services.ec2.model.Tag
import aws.sdk.kotlin.services.ec2.model.TagSpecification
import aws.sdk.kotlin.services.ec2.paginators.describeInstancesPaginated
import aws.sdk.kotlin.services.ec2.paginators.reservations
import aws.sdk.kotlin.services.ec2.replaceNetworkAclAssociation
import aws.sdk.kotlin.services.eks.EksClient
import aws.sdk.kotlin.services.eks.describeCluster
import aws.sdk.kotlin.services.eks.paginators.listNodegroupsPaginated
import aws.sdk.kotlin.services.eks.paginators.nodegroups
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.tongfei.progressbar.ProgressBar

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
                help = "The duration for which the subnet will be inaccessible, e.g. 10m"
            )
            .convert { Duration.parse(it) }
            .default(2.minutes)

    private val t = Terminal()

    override fun run() = runBlocking {
        EksClient.fromEnvironment { region = this@DisableControlPlaneCommunicationInAz.region }
            .use { eksClient ->
                Ec2Client.fromEnvironment {
                        region = this@DisableControlPlaneCommunicationInAz.region
                    }
                    .use { ec2Client -> triggerScenario(eksClient, ec2Client) }
            }
    }

    private suspend fun triggerScenario(eksClient: EksClient, ec2Client: Ec2Client) =
        withContext(Dispatchers.Default) {
            ensureClusterExists(eksClient)

            val largestSubnet = findLargestSubnet(eksClient, ec2Client)

            val (networkAclWithDenyAllId, originalNetworkAclAssociation) =
                prepareNetworkAclsForDisablement(ec2Client, largestSubnet)

            try {
                t.println(
                    "Disabling subnet ${green(largestSubnet.tags?.name() ?: largestSubnet.subnetId!!)}(${largestSubnet.subnetId}) " +
                        "used by ${green(clusterName)} " +
                        "by attaching NACL $networkAclWithDenyAllId. " +
                        "Original NACL: ${originalNetworkAclAssociation.networkAclId} " +
                        "(name: ${getNetworkAclName(originalNetworkAclAssociation.networkAclId!!, ec2Client)})"
                )
                disableSubnet(ec2Client, originalNetworkAclAssociation, networkAclWithDenyAllId)

                t.println(
                    "Now, the nodes on the selected subnet should be in NotReady state for $outageDuration"
                )
                startCountdownTimer()
            } finally {
                // Switch back to the original Network ACL
                t.println(
                    "Scenario cleanup, removing the deny-all NACL ($networkAclWithDenyAllId) " +
                        "and enabling the AZ by switching to NACL ${originalNetworkAclAssociation.networkAclId}"
                )
                enableSubnet(
                    largestSubnet,
                    ec2Client,
                    originalNetworkAclAssociation,
                    networkAclWithDenyAllId
                )
            }
        }

    private suspend fun enableSubnet(
        largestSubnet: Subnet,
        ec2Client: Ec2Client,
        originalNetworkAclAssociation: NetworkAclAssociation,
        networkAclWithDenyAllId: String
    ) {
        val newNetworkAclAssociation =
            getCurrentNetworkAclAssociationForSubnet(largestSubnet, ec2Client)
        ec2Client.replaceNetworkAclAssociation {
            associationId = newNetworkAclAssociation.networkAclAssociationId
            networkAclId = originalNetworkAclAssociation.networkAclId
        }

        ec2Client.deleteNetworkAcl { networkAclId = networkAclWithDenyAllId }
    }

    private suspend fun CoroutineScope.startCountdownTimer() {
        val startedAt = Instant.now()
        val expectedEndAt = Instant.now().plus(outageDuration.toJavaDuration())

        val durationMilli = (expectedEndAt - Instant.now()).inWholeMilliseconds
        ProgressBar("Nodes in NotReady state for $outageDuration", durationMilli).use { pb ->
            while (isActive && Instant.now().isBefore(expectedEndAt)) {
                pb.stepTo((Instant.now() - startedAt).inWholeMilliseconds)
                delay(1.seconds)
            }
            pb.stepTo(durationMilli)
        }
    }

    private suspend fun ensureClusterExists(eksClient: EksClient) {
        requireNotNull(eksClient.describeCluster { name = clusterName }.cluster) {
            "Cluster $clusterName not found"
        }
    }

    private suspend fun prepareNetworkAclsForDisablement(
        ec2Client: Ec2Client,
        largestSubnet: Subnet
    ): Pair<String, NetworkAclAssociation> {
        val networkAclWithDenyAll =
            ec2Client.createNetworkAcl {
                vpcId = largestSubnet.vpcId
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
        val networkAclWithDenyAllId = requireNotNull(networkAclWithDenyAll.networkAcl?.networkAclId)

        val originalNetworkAclAssociation =
            getCurrentNetworkAclAssociationForSubnet(largestSubnet, ec2Client)
        return Pair(networkAclWithDenyAllId, originalNetworkAclAssociation)
    }

    private suspend fun disableSubnet(
        ec2Client: Ec2Client,
        originalNetworkAclAssociation: NetworkAclAssociation,
        networkAclWithDenyAllId: String
    ) {
        ec2Client.replaceNetworkAclAssociation {
            associationId = originalNetworkAclAssociation.networkAclAssociationId
            networkAclId = networkAclWithDenyAllId
        }
    }

    /** Finds the subnet that has the most instances running. */
    private suspend fun findLargestSubnet(eksClient: EksClient, ec2Client: Ec2Client): Subnet {
        val subnetsWithInstanceCounts =
            eksClient
                .listNodegroupsPaginated {
                    clusterName = this@DisableControlPlaneCommunicationInAz.clusterName
                }
                .nodegroups()
                .flatMapMerge { getNodeGroupInstanceSubnets(ec2Client, it) }
                .toList()
                .groupingBy { it }
                .eachCount()

        t.println(
            table {
                header { row("Subnet ID", "Number of running instances") }
                body {
                    subnetsWithInstanceCounts.forEach { (subnetId, instances) ->
                        row(subnetId, instances)
                    }
                }
            }
        )

        val largestSubnetId = subnetsWithInstanceCounts.maxByOrNull { it.value }?.key

        requireNotNull(largestSubnetId) {
            "Cannot find any subnet with at least one instance running."
        }

        return findSubnet(ec2Client, largestSubnetId)
    }

    private suspend fun findSubnet(ec2Client: Ec2Client, subnetId: String): Subnet {
        val subnet =
            ec2Client.describeSubnets { subnetIds = listOf(subnetId) }.subnets?.firstOrNull()
        return requireNotNull(subnet) { "Cannot find subnet $subnetId" }
    }

    /**
     * For a given node group, returns the list of subnet IDs that are associated with its
     * instances.
     */
    private fun getNodeGroupInstanceSubnets(ec2Client: Ec2Client, it: String) =
        ec2Client
            .describeInstancesPaginated {
                filters =
                    listOf(
                        Filter {
                            name = "tag:eks:nodegroup-name"
                            this.values = listOf(it)
                        },
                        Filter {
                            name = "tag:aws:eks:cluster-name"
                            this.values = listOf(clusterName)
                        }
                    )
            }
            .reservations()
            .flatMapMerge {
                val subnetIds =
                    it.instances
                        ?.filter { instance -> instance.state?.code == 16 }
                        ?.flatMap { instance ->
                            instance.networkInterfaces?.mapNotNull { instanceNetworkInterface ->
                                instanceNetworkInterface.subnetId
                            }
                                ?: emptyList()
                        }
                subnetIds?.asFlow() ?: emptyFlow()
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

    private fun List<Tag>?.name(): String? = this?.firstOrNull { it.key == "Name" }?.value

    private suspend fun getNetworkAclName(networkAclId: String, ec2Client: Ec2Client): String =
        ec2Client
            .describeNetworkAcls { this.networkAclIds = listOf(networkAclId) }
            .networkAcls
            ?.firstOrNull()
            ?.tags
            ?.name()
            ?: "-"
}

operator fun Instant.minus(other: Instant): Duration =
    JavaDuration.between(other, this).toKotlinDuration()
