package com.example.state

import com.example.contract.TrancheBalanceContract
import com.example.model.Tranche
import com.example.schema.TrancheSchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.keys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.security.PublicKey
import java.util.*

data class TrancheBalanceState(val tranche: Tranche,
                               val balance: Amount<Issued<Currency>>,
                               val agent: Party,
                               val owner: Party,
                               override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {

    override val contract get() = TrancheBalanceContract()

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner, agent)

    /** Tells the vault to track a state if we are one of the parties involved. */
    override fun isRelevant(ourKeys: Set<PublicKey>) = ourKeys.intersect(participants.flatMap { it.owningKey.keys })
            .isNotEmpty()

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TrancheSchemaV1 -> TrancheSchemaV1.PersistentTranche(
                    referenceNumber = this.tranche.referenceNumber,
                    borrower = this.tranche.borrower,
                    interestRate = this.tranche.interestRate,
                    exchangeRate = this.tranche.exchangeRate,
                    irFixingDate = this.tranche.irFixingDate,
                    erFixingDate = this.tranche.erFixingDate,
                    startDate = this.tranche.startDate,
                    endDate = this.tranche.endDate,
                    txCurrency = this.tranche.txCurrency
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TrancheSchemaV1)
}