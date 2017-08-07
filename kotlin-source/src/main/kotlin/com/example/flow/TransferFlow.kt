package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TrancheContract
import com.example.state.TrancheState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow

object TransferFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val stateRef: StateRef): FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Obtaining tranche from vault.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object COLLECTING : ProgressTracker.Step("Collecting counter party signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }
            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    COLLECTING,
                    FINALISING
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val blStateAndRefs = serviceHub.vaultQueryService.queryBy<TrancheState>()
                    .states.associateBy( {it.ref}, {it} )

            val blStateAndRef = blStateAndRefs[stateRef] ?: throw IllegalArgumentException("TrancheState with StateRef $stateRef not found.")
            val inputBL = blStateAndRef.state.data

            require(serviceHub.myInfo.legalIdentity == inputBL.owner) { "Tranche transfer can only be initiated by the Tranche owner." }

            val outputBL = inputBL.withNewOwner(inputBL.owner)

            var signers = listOf(inputBL.owner.owningKey, inputBL.agent.owningKey)

            // Generate an unsigned transaction.
            val txCommand = Command(TrancheContract.Commands.Move(), listOf(inputBL.agent.owningKey, inputBL.owner.owningKey))
            val unsignedTx = TransactionType.General.Builder(notary).withItems(blStateAndRef, outputBL, txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(unsignedTx, signers)

            // Stage 4. Collect signature from shipping company and importer bank and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(partSignedTx, COLLECTING.childProgressTracker()))

            // Stage 5. Notarise and record, the transaction in our vaults.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx)).single()
        }
    }

    @InitiatedBy(TransferFlow.Initiator::class)
    class Responder(val otherParty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherParty) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Add some checking.
                }
            }

            val stx = subFlow(flow)

            return waitForLedgerCommit(stx.id)
        }
    }
}