package com.example.contract

import com.example.state.TrancheBalanceState
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.LedgerTransaction

open class TrancheBalanceContract : Contract {
    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Issue -> {
                // Issuance verification logic.
                requireThat {
                    // Generic constraints around the tranche transaction.
                    "No inputs should be consumed when issuing an tranche." using (tx.inputs.isEmpty())
                    "Only one output state should be created." using (tx.outputs.size == 2)
                    val out = tx.outputsOfType<TrancheBalanceState>().single()
                    //"The exporter and the counterParty cannot be the same entity." using (out.exporter != out.shippingCompany)
                    //"The exporter and the importerBank cannot be the same entity." using (out.exporter != out.importerBank)
                    //"The exporter and the counterParty must be signers." using (command.signers.containsAll(listOf(out.exporter.owningKey, out.shippingCompany.owningKey)))

                    // tranche-specific constraints.
                    "The tranche's total amount must be non-negative." using (out.balance.quantity > 0)
                    "The tranche's available amount must be non-negative." using (out.balance.quantity >= 0)
                }
            }
            is Commands.Move -> {
                // Transfer verification logic.
                requireThat {
                    "Only one input should be consumed when move a tranche." using (tx.inputs.size == 1)
                    "Two output states should be created." using (tx.outputs.size == 2)
                    val input = tx.inputsOfType<TrancheBalanceState>().single()
                    val out1 = tx.outputsOfType<TrancheBalanceState>()[0]
                    val out2 = tx.outputsOfType<TrancheBalanceState>()[1]
                    "Owner must not be changed for remained tranche." using (input.owner == out1.owner)
                    "Owner must be changed when transfer a tranche." using (input.owner != out2.owner)
                    "The tranche's available amount must be non-negative." using (out1.balance.quantity >= 0 && out2.balance.quantity >= 0)
                }
            }
        }

    }

    /**
     * This contract implements commands: Issue, Move.
     */
    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    /** This is a reference to the underlying legal contract template and associated parameters. */
    override val legalContractReference: SecureHash = SecureHash.sha256("tranche balance contract template and params")
}