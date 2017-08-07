package com.example.api

import com.example.flow.IssueFlow
import com.example.flow.TransferFlow
import com.example.model.Tranche
import com.example.state.TrancheState
import net.corda.client.rpc.notUsed
import net.corda.core.contracts.*
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import java.math.BigDecimal
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val NOTARY_NAME = "CN=Controller,O=R3,L=London,C=UK"

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class ExampleApi(val services: CordaRPCOps) {
    private val myLegalName: X500Name = services.nodeIdentity().legalIdentity.name

    companion object {
        private val logger: Logger = loggerFor<ExampleApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<X500Name>> {
        val (nodeInfo, nodeUpdates) = services.networkMapUpdates()
        nodeUpdates.notUsed()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentity.name }
                .filter { it != myLegalName && it.toString() != NOTARY_NAME })
    }

    /**
     * Displays all tranche states that exist in the node's vault.
     */
    @GET
    @Path("SLs")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBLs(): List<StateAndRef<TrancheState>> {
        val vaultStates = services.vaultQueryBy<TrancheState>()
        return vaultStates.states
    }

    @PUT
    @Path("{currency}/{amount}/issue-tranche")
    fun issueBL(tranche: Tranche, @PathParam("currency") currency: String,
                @PathParam("amount") amount: String): Response {
        val totalAmount = Amount<Issued<Currency>>(
                amount.toLong(),
                BigDecimal(amount),
                Issued(PartyAndReference(services.nodeIdentity().legalIdentity, OpaqueBytes.of(0)), Currency.getInstance(currency))
        )
        val state = TrancheState(
                tranche,
                totalAmount,
                services.nodeIdentity().legalIdentity,
                totalAmount,
                services.nodeIdentity().legalIdentity)

        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(IssueFlow.Initiator::class.java, state)
            flowHandle.progress.subscribe { println(">> $it") }

            // The line below blocks and waits for the future to resolve.
            val result = flowHandle
                    .returnValue
                    .getOrThrow()

            Response.Status.CREATED to "Transaction id ${result.id} committed to ledger."

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.Status.BAD_REQUEST to "Transaction failed."
        }

        return Response.status(status).entity(msg).build()
    }

    @PUT
    @Path("transfer-tranche")
    fun transferBL(stateRef: StateRef): Response {
        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(TransferFlow.Initiator::class.java, stateRef)
            flowHandle.progress.subscribe { println(">> $it") }

            // The line below blocks and waits for the future to resolve.
            val result = flowHandle
                    .returnValue
                    .getOrThrow()

            Response.Status.CREATED to "Transaction id ${result.id} committed to ledger."

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.Status.BAD_REQUEST to "Transaction failed."
        }

        return Response.status(status).entity(msg).build()
    }
}