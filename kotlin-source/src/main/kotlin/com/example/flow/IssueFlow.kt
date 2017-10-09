package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TrancheBalanceContract
import com.example.contract.TrancheBalanceContract.Companion.TRANCHEBALANCE_CONTRACT_ID
import com.example.contract.TrancheContract
import com.example.contract.TrancheContract.Companion.TRANCHE_CONTRACT_ID
import com.example.state.TrancheBalanceState
import com.example.state.TrancheState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object IssueFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val trancheState: TrancheState, val trancheBalanceState: TrancheBalanceState) : FlowLogic<SignedTransaction>() {
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
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(TrancheContract.Commands.Issue(), listOf(trancheState.agent.owningKey))
            val txCommandBalance = Command(TrancheBalanceContract.Commands.Issue(), listOf(trancheBalanceState.agent.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .withItems(StateAndContract(trancheState, TRANCHE_CONTRACT_ID), txCommand, StateAndContract(trancheBalanceState, TRANCHEBALANCE_CONTRACT_ID), txCommandBalance)
            //val signers = listOf(trancheState.agent.owningKey, notary.owningKey)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = FINALIZING_TRANSACTION
            return subFlow(FinalityFlow(partSignedTx))
        }
    }

}