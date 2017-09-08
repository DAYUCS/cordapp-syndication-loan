package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TrancheBalanceContract
import com.example.contract.TrancheContract
import com.example.state.TrancheBalanceState
import com.example.state.TrancheState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
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

            private val logger: Logger = loggerFor<Initiator>()
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

            val builder = TransactionType.General.Builder(notary)

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Get TrancheState
            val blStateAndRefs = serviceHub.vaultQueryService.queryBy<TrancheState>()
                    .states.associateBy({ it.ref }, { it })

            val blStateAndRef = blStateAndRefs[stateRef] ?: throw IllegalArgumentException("TrancheState with StateRef $stateRef not found.")
            val inputBL = blStateAndRef.state.data
            val referenceNumber = inputBL.tranche.referenceNumber
            val movement = Amount(
                    moveAmount.toLong(),
                    BigDecimal(100),
                    inputBL.totalAmount.token
            )

            require(serviceHub.myInfo.legalIdentity == inputBL.agent) { "Tranche transfer can only be initiated by the agent bank." }

            // Get TrancheBalanceState
            val blBalanceStateAndRefs = serviceHub.vaultQueryService.queryBy<TrancheBalanceState>()
                    .states.associateBy({ it.ref }, { it })
                    .filterValues { it.state.data.tranche.referenceNumber.contentEquals(referenceNumber) }

            logger.info("Balance Records Number: ".plus(blBalanceStateAndRefs.size))
            logger.info("Target Bank:".plus(newOwner.owningKey))
            logger.info("Agent Bank:".plus(inputBL.agent.owningKey))
            //var agentBalanceExist = false
            var targetBalanceExist = false
            for (item in blBalanceStateAndRefs) {
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
                    builder.addOutputState(trancheBalanceState)
                } else if (item.value.state.data.owner.owningKey == inputBL.agent.owningKey) {
                    builder.addInputState(item.value)
                    //agentBalanceExist = true
                    val trancheBalanceState = TrancheBalanceState(
                            item.value.state.data.tranche,
                            item.value.state.data.balance.minus(movement),
                            item.value.state.data.agent,
                            item.value.state.data.owner
                    )
                    builder.addOutputState(trancheBalanceState)
                }
            }

            if (!targetBalanceExist) {
                val trancheBalanceState = TrancheBalanceState(
                        inputBL.tranche,
                        movement,
                        inputBL.agent,
                        newOwner
                )
                builder.addOutputState(trancheBalanceState)
            }

            val remainedBL = inputBL.move(inputBL.amount.minus(movement), inputBL.agent)

            val outputBL = inputBL.move(movement, newOwner)
            //outputBL.participants.plus(inputBL.agent)

            val txCommand = Command(TrancheContract.Commands.Move(), listOf(inputBL.agent.owningKey, newOwner.owningKey))
            val txCommandBalance = Command(TrancheBalanceContract.Commands.Move(), listOf(inputBL.agent.owningKey, newOwner.owningKey))
            builder.addInputState(blStateAndRef)
            builder.addCommand(txCommand)
            builder.addCommand(txCommandBalance)
            builder.addOutputState(remainedBL)
            builder.addOutputState(outputBL)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            //unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            builder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            //val partSignedTx = serviceHub.signInitialTransaction(unsignedTx)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 4. Collect signature from parties and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            progressTracker.currentStep = COLLECTING
            //val stx = subFlow(CollectSignaturesFlow(partSignedTx, COLLECTING.childProgressTracker()))
            val stx = subFlow(CollectSignaturesFlow(ptx))

            // Stage 5. Notarise and record, the transaction in our vaults.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, setOf(inputBL.agent, newOwner))).single()
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