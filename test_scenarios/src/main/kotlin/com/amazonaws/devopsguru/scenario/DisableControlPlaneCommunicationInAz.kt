@file:JvmName("Main")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.amazonaws.devopsguru.scenario

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
import com.amazonaws.devopsguru.util.minus
import com.amazonaws.devopsguru.util.plus
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle

private const val EC2_RUNNING_INSTANCE_CODE = 16

@OptIn(ExperimentalCoroutinesApi::class)
class DisableControlPlaneCommunicationInAz(
    private val clusterName: String,
    private val outageDuration: Duration,
    private val eksClient: EksClient,
    private val ec2Client: Ec2Client
) {

    private val t = Terminal()

    private val scenarioActivated = AtomicBoolean(false)

    suspend fun triggerScenario() =
        withContext(Dispatchers.Default) {
            ensureClusterExists()

            val largestSubnet = findLargestSubnet()

            val (networkAclWithDenyAllId, originalNetworkAclAssociation) =
                prepareNetworkAclsForDisablement(largestSubnet)

            try {
                t.println(
                    "Disabling subnet ${green(largestSubnet.tags?.name() ?: largestSubnet.subnetId!!)}(${largestSubnet.subnetId}) " +
                        "used by ${green(clusterName)} " +
                        "by attaching NACL $networkAclWithDenyAllId. " +
                        "Original NACL: ${originalNetworkAclAssociation.networkAclId} " +
                        "(name: ${getNetworkAclName(originalNetworkAclAssociation.networkAclId!!)})"
                )

                cleanUpWhenTerminated(
                    largestSubnet = largestSubnet,
                    originalNetworkAclAssociation = originalNetworkAclAssociation,
                    networkAclWithDenyAllId = networkAclWithDenyAllId
                )
                disableSubnet(originalNetworkAclAssociation, networkAclWithDenyAllId)

                t.println(
                    "Now, the nodes on the selected subnet should be in NotReady state for $outageDuration"
                )
                startCountdownTimer()
            } finally {
                enableSubnet(
                    largestSubnet = largestSubnet,
                    originalNetworkAclAssociation = originalNetworkAclAssociation,
                    networkAclWithDenyAllId = networkAclWithDenyAllId
                )
                withContext(Dispatchers.IO) {
                    eksClient.close()
                    ec2Client.close()
                }
            }
        }

    /**
     * Ensure that we clean up (re-enable the subnet) even when the scenario is interrupted with
     * CTRL+C.
     */
    private suspend fun cleanUpWhenTerminated(
        largestSubnet: Subnet,
        originalNetworkAclAssociation: NetworkAclAssociation,
        networkAclWithDenyAllId: String
    ) {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    val exitCode = Thread.currentThread().name.toIntOrNull()
                    if (exitCode != 0 && scenarioActivated.get()) {
                        runBlocking {
                            enableSubnet(
                                largestSubnet,
                                originalNetworkAclAssociation,
                                networkAclWithDenyAllId
                            )
                        }
                    }
                }
            )
    }

    /** Switch back to the original Network ACL and re-enable the subnet. */
    private suspend fun enableSubnet(
        largestSubnet: Subnet,
        originalNetworkAclAssociation: NetworkAclAssociation,
        networkAclWithDenyAllId: String
    ) {
        t.println(
            "Scenario cleanup, removing the deny-all NACL ($networkAclWithDenyAllId) " +
                "and enabling the AZ by switching to NACL ${originalNetworkAclAssociation.networkAclId}"
        )
        val newNetworkAclAssociation = getCurrentNetworkAclAssociationForSubnet(largestSubnet)
        ec2Client.replaceNetworkAclAssociation {
            associationId = newNetworkAclAssociation.networkAclAssociationId
            networkAclId = originalNetworkAclAssociation.networkAclId
        }

        ec2Client.deleteNetworkAcl { networkAclId = networkAclWithDenyAllId }
        scenarioActivated.set(false)
    }

    /** Display a progress bar that counts up to the specified duration. */
    private suspend fun CoroutineScope.startCountdownTimer() {
        ProgressBarBuilder()
            .continuousUpdate()
            .setTaskName("Nodes in NotReady state for $outageDuration")
            .setInitialMax(outageDuration.inWholeMilliseconds)
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
            .build()
            .use { pb ->
                val startedAt = Instant.now()
                val expectedEndAt = Instant.now() + outageDuration

                while (isActive && Instant.now().isBefore(expectedEndAt)) {
                    pb.stepTo((Instant.now() - startedAt).inWholeMilliseconds)
                    delay(0.1.seconds)
                }

                pb.stepTo(outageDuration.inWholeMilliseconds)
            }
    }

    private suspend fun ensureClusterExists() {
        requireNotNull(eksClient.describeCluster { name = clusterName }.cluster) {
            "Cluster $clusterName not found"
        }
    }

    /**
     * Fetch the current Network ACL association for the [subnet] and create a new Network ACL with
     * "Deny All" rules.
     *
     * @return the ID of the new Network ACL with "Deny All" rules and the original Network ACL.
     */
    private suspend fun prepareNetworkAclsForDisablement(
        subnet: Subnet
    ): Pair<String, NetworkAclAssociation> {
        val networkAclWithDenyAll =
            ec2Client.createNetworkAcl {
                vpcId = subnet.vpcId
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

        val originalNetworkAclAssociation = getCurrentNetworkAclAssociationForSubnet(subnet)
        return Pair(networkAclWithDenyAllId, originalNetworkAclAssociation)
    }

    /**
     * Replace the current Network ACL association with the new Network ACL created with "Deny All"
     * rule.
     */
    private suspend fun disableSubnet(
        originalNetworkAclAssociation: NetworkAclAssociation,
        networkAclWithDenyAllId: String
    ) {
        ec2Client.replaceNetworkAclAssociation {
            associationId = originalNetworkAclAssociation.networkAclAssociationId
            networkAclId = networkAclWithDenyAllId
        }
        scenarioActivated.set(true)
    }

    /** Finds the subnet that has the most EC2 instances running. */
    private suspend fun findLargestSubnet(): Subnet {
        val subnetsWithInstanceCounts =
            eksClient
                .listNodegroupsPaginated {
                    clusterName = this@DisableControlPlaneCommunicationInAz.clusterName
                }
                .nodegroups()
                .flatMapMerge { getNodeGroupInstanceSubnets(it) }
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

        return findSubnetById(largestSubnetId)
    }

    private suspend fun findSubnetById(subnetId: String): Subnet {
        val subnet =
            ec2Client.describeSubnets { subnetIds = listOf(subnetId) }.subnets?.firstOrNull()
        return requireNotNull(subnet) { "Cannot find subnet $subnetId" }
    }

    /**
     * For a given [nodeGroupName], returns the list of subnet IDs that are associated with its
     * instances.
     */
    private fun getNodeGroupInstanceSubnets(nodeGroupName: String) =
        ec2Client
            .describeInstancesPaginated {
                filters =
                    listOf(
                        Filter {
                            name = "tag:eks:nodegroup-name"
                            this.values = listOf(nodeGroupName)
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
                        ?.filter { instance -> instance.state?.code == EC2_RUNNING_INSTANCE_CODE }
                        ?.flatMap { instance ->
                            instance.networkInterfaces?.mapNotNull { instanceNetworkInterface ->
                                instanceNetworkInterface.subnetId
                            }
                                ?: emptyList()
                        }
                subnetIds?.asFlow() ?: emptyFlow()
            }

    private suspend fun getCurrentNetworkAclAssociationForSubnet(
        subnet: Subnet
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

    private suspend fun getNetworkAclName(networkAclId: String): String =
        ec2Client
            .describeNetworkAcls { this.networkAclIds = listOf(networkAclId) }
            .networkAcls
            ?.firstOrNull()
            ?.tags
            ?.name()
            ?: "-"
}

private fun List<Tag>?.name(): String? = this?.firstOrNull { it.key == "Name" }?.value
