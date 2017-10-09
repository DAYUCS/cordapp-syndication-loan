package com.example.api

import com.example.flow.IssueFlow
import com.example.flow.TransferFlow
import com.example.model.Tranche
import com.example.state.TrancheBalanceState
import com.example.state.TrancheState
import net.corda.core.contracts.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import org.slf4j.Logger
import java.math.BigDecimal
import java.util.Currency
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val NOTARY_NAME = "Controller"
val NETWORK_MAP_NAME = "Network Map Service"

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class ExampleApi(val services: CordaRPCOps) {
    private val myLegalName: CordaX500Name = services.nodeInfo().legalIdentities.first().name

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
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = services.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it != myLegalName && it.organisation != NOTARY_NAME && it.organisation != NETWORK_MAP_NAME })
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

    /**
     * Displays all trancheBalance states that exist in the node's vault.
     */
    @GET
    @Path("SLBALs")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBLBALs(): List<StateAndRef<TrancheBalanceState>> {
        val vaultStates = services.vaultQueryBy<TrancheBalanceState>()
        return vaultStates.states
    }

    @PUT
    @Path("{currency}/{amount}/issue-tranche")
    fun issueBL(tranche: Tranche, @PathParam("currency") currency: String,
                @PathParam("amount") amount: String): Response {
        //logger.info("Currency:".plus(currency))
        //logger.info("Amount:".plus(amount))
        //logger.info("Amount to Long:".plus(amount.toLong()))
        //logger.info("Amount to BigDecimal".plus(BigDecimal(amount)))
        val totalAmount = Amount<Issued<Currency>>(
                amount.toLong(),
                BigDecimal(100),
                Issued(PartyAndReference(services.nodeInfo().legalIdentities.first(), OpaqueBytes.of(0)), Currency.getInstance(currency))
        )
        //logger.info("Total Amount Quantity:".plus(totalAmount.quantity))
        //logger.info("Total Amount Display Token Size:".plus(totalAmount.displayTokenSize))
        val trancheState = TrancheState(
                tranche,
                totalAmount,
                services.nodeInfo().legalIdentities.first(),
                totalAmount,
                services.nodeInfo().legalIdentities.first())
        logger.info("Tranche Amount Quantity:".plus(trancheState.totalAmount.quantity))
        logger.info("Tranche Amount Token Size:".plus(trancheState.totalAmount.displayTokenSize))
        val trancheBalanceState = TrancheBalanceState(
                tranche,
                totalAmount,
                services.nodeInfo().legalIdentities.first(),
                services.nodeInfo().legalIdentities.first()
                )
        logger.info("Tranche Balance Quantity:".plus(trancheBalanceState.balance.quantity))
        logger.info("Tranche Balance Token Size:".plus(trancheBalanceState.balance.displayTokenSize))
        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(IssueFlow.Initiator::class.java, trancheState, trancheBalanceState)
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
    @Path("{bank}/{amount}/transfer-tranche")
    fun transferBL(stateRef: StateRef, @PathParam("bank") bank: CordaX500Name,
                   @PathParam("amount") amount: String): Response {
        val toBank = services.wellKnownPartyFromX500Name(bank) ?:
                return Response.status(Response.Status.BAD_REQUEST).entity("Party named $bank cannot be found.\n").build()
        val (status, msg) = try {
            val flowHandle = services
                    .startTrackedFlowDynamic(TransferFlow.Initiator::class.java, stateRef, toBank, amount)
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