package com.example.state

import com.example.model.Tranche
import com.example.schema.TrancheSchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.util.*

data class TrancheBalanceState(val tranche: Tranche,
                               val balance: Amount<Issued<Currency>>,
                               val agent: Party,
                               val owner: Party,
                               override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner, agent)

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