package com.example.api

import com.example.flow.ExampleFlow
import com.example.flow.ShippingFlow
import com.example.model.BL
import com.example.state.BLState
import net.corda.client.rpc.notUsed
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
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
     * Displays all bl states that exist in the node's vault.
     */
    @GET
    @Path("bls")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBLs(): List<StateAndRef<BLState>> {
        val vaultStates = services.vaultQueryBy<BLState>()
        return vaultStates.states
    }

    /**
     * Initiates a flow to agree an bl between two parties.
     *
     * Once the flow finishes it will have written the bl to ledger. Both the exporter and the counterParty will be able to
     * see it when calling /api/example/bls on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("{counterParty}/{importerBank}/issue-bl")
    fun issueBL(bl: BL, @PathParam("counterParty") shippingCompanyName: X500Name,
                @PathParam("importerBank") importerBankName: X500Name): Response {
        val shippingCommpany = services.partyFromX500Name(shippingCompanyName)
        if (shippingCommpany == null) {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        val importerBank = services.partyFromX500Name(importerBankName)
        if (importerBank == null) {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        val state = BLState(
                bl,
                services.nodeIdentity().legalIdentity,
                shippingCommpany,
                importerBank,
                shippingCommpany)

        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(ExampleFlow.Initiator::class.java, state, shippingCommpany)
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
    @Path("transfer-bl")
    fun transferBL(stateRef: StateRef): Response {
        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(ShippingFlow.Initiator::class.java, stateRef)
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