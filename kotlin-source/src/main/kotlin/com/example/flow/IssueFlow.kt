package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TrancheContract
import com.example.state.TrancheState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.FinalityFlow

object IssueFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val trancheState: TrancheState) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new tranche.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with notary and our private key.")
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Finalizing proposed transaction.")

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALIZING_TRANSACTION
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

            // Obtain a reference to myself
            val issuer = serviceHub.myInfo.legalIdentity

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(TrancheContract.Commands.Issue(), listOf(trancheState.agent.owningKey))
            val unsignedTx = TransactionType.General.Builder(notary).withItems(trancheState, txCommand)
            val signers = listOf(notary.owningKey, issuer.owningKey)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(unsignedTx, signers)

            // Stage 4.
            progressTracker.currentStep = FINALIZING_TRANSACTION
            return subFlow(FinalityFlow(partSignedTx)).single()
        }
    }

}