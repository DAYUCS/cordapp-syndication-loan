package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TrancheBalanceContract
import com.example.contract.TrancheBalanceContract.Companion.TRANCHEBALANCE_CONTRACT_ID
import com.example.contract.TrancheContract
import com.example.contract.TrancheContract.Companion.TRANCHE_CONTRACT_ID
import com.example.state.TrancheBalanceState
import com.example.state.TrancheState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.math.BigDecimal

object TransferFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val stateRef: StateRef, val newOwner: Party, val moveAmount: String) : FlowLogic<SignedTransaction>() {
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
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val builder = TransactionBuilder(notary)

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Get TrancheState
            val trancheStateAndRefs = serviceHub.vaultService.queryBy<TrancheState>()
                    .states.associateBy({ it.ref }, { it })

            val trancheStateAndRef = trancheStateAndRefs[stateRef] ?: throw IllegalArgumentException("TrancheState with StateRef $stateRef not found.")
            val inputTranche = trancheStateAndRef.state.data
            val referenceNumber = inputTranche.tranche.referenceNumber
            val movement = Amount(
                    moveAmount.toLong(),
                    BigDecimal(100),
                    inputTranche.totalAmount.token
            )

            require(serviceHub.myInfo.legalIdentities.first() == inputTranche.agent) { "Tranche transfer can only be initiated by the agent bank." }

            // Get TrancheBalanceState
            val trancheBalanceStateAndRefs = serviceHub.vaultService.queryBy<TrancheBalanceState>()
                    .states.associateBy({ it.ref }, { it })
                    .filterValues { it.state.data.tranche.referenceNumber.contentEquals(referenceNumber) }

            logger.info("Balance Records Number: ".plus(trancheBalanceStateAndRefs.size))
            logger.info("Target Bank:".plus(newOwner.owningKey))
            logger.info("Agent Bank:".plus(inputTranche.agent.owningKey))

            var targetBalanceExist = false
            for (item in trancheBalanceStateAndRefs) {
                logger.info("Item Owner:".plus(item.value.state.data.owner.owningKey))
                if (item.value.state.data.owner.owningKey == newOwner.owningKey) {
                    builder.addInputState(item.value)
                    targetBalanceExist = true
                    val trancheBalanceState = TrancheBalanceState(
                            item.value.state.data.tranche,
                            item.value.state.data.balance.plus(movement),
                            item.value.state.data.agent,
                            item.value.state.data.owner
                    )
                    builder.addOutputState(TransactionState(trancheBalanceState, TRANCHEBALANCE_CONTRACT_ID, notary))
                } else if (item.value.state.data.owner.owningKey == inputTranche.agent.owningKey) {
                    builder.addInputState(item.value)
                    val trancheBalanceState = TrancheBalanceState(
                            item.value.state.data.tranche,
                            item.value.state.data.balance.minus(movement),
                            item.value.state.data.agent,
                            item.value.state.data.owner
                    )
                    builder.addOutputState(TransactionState(trancheBalanceState, TRANCHEBALANCE_CONTRACT_ID, notary))
                }
            }

            if (!targetBalanceExist) {
                val trancheBalanceState = TrancheBalanceState(
                        inputTranche.tranche,
                        movement,
                        inputTranche.agent,
                        newOwner
                )
                builder.addOutputState(TransactionState(trancheBalanceState, TRANCHEBALANCE_CONTRACT_ID, notary))
            }

            val remainedTranche = inputTranche.withNewOwnerAndAmount(inputTranche.amount.minus(movement), inputTranche.agent)

            val outputTranche = inputTranche.withNewOwnerAndAmount(movement, newOwner)

            val txCommand = Command(TrancheContract.Commands.Move(), listOf(inputTranche.agent.owningKey, newOwner.owningKey))
            val txCommandBalance = Command(TrancheBalanceContract.Commands.Move(), listOf(inputTranche.agent.owningKey, newOwner.owningKey))
            builder.addInputState(trancheStateAndRef)
            builder.addCommand(txCommand)
            builder.addCommand(txCommandBalance)
            builder.addOutputState(TransactionState(remainedTranche, TRANCHE_CONTRACT_ID, notary))
            builder.addOutputState(TransactionState(outputTranche, TRANCHE_CONTRACT_ID, notary))

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            //unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            builder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            //val partSignedTx = serviceHub.signInitialTransaction(unsignedTx)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 4. Collect signature from parties and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val otherPartyFlow = initiateFlow(newOwner)
            progressTracker.currentStep = COLLECTING
            //val stx = subFlow(CollectSignaturesFlow(partSignedTx, COLLECTING.childProgressTracker()))
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(otherPartyFlow), COLLECTING.childProgressTracker()))

            // Stage 5. Notarise and record, the transaction in our vaults.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherPartyFlow) {
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