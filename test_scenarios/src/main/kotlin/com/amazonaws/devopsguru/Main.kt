@file:JvmName("Main")

package com.amazonaws.devopsguru

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.eks.EksClient
import com.amazonaws.devopsguru.scenario.DisableControlPlaneCommunicationInAz
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    MakeNodeUnhealthy().subcommands(DisableControlPlaneCommunicationInAzCommand()).main(args)
}

class MakeNodeUnhealthy : CliktCommand() {
    override fun run() = Unit
}

class DisableControlPlaneCommunicationInAzCommand :
    CliktCommand(
        help =
            """Trigger a scenario where nodes in one Availability Zone are unavailable
               |because they cannot communicate with the control plane"""
                .trimMargin()
                .replace('\n', ' '),
    ) {
    private val clusterName by
        option("-c", "--cluster-name", help = "Names of the EKS cluster").required()
    private val region by option("--region", "-r", help = "AWS region")
    private val outageDuration by
        option(
                "--outage-duration",
                "-d",
                help = "The duration for which the subnet will be inaccessible, e.g. 10m"
            )
            .convert { Duration.parse(it) }
            .default(2.minutes)

    override fun run() = runBlocking {
        val eksClient =
            EksClient.fromEnvironment {
                region = this@DisableControlPlaneCommunicationInAzCommand.region
            }
        val ec2Client =
            Ec2Client.fromEnvironment {
                region = this@DisableControlPlaneCommunicationInAzCommand.region
            }

        DisableControlPlaneCommunicationInAz(
                clusterName = clusterName,
                outageDuration = outageDuration,
                eksClient = eksClient,
                ec2Client = ec2Client
            )
            .triggerScenario()
    }
}
