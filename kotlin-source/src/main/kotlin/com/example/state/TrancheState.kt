package com.example.state

import com.example.contract.TrancheContract
import com.example.model.Tranche
import com.example.schema.TrancheSchemaV1
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.crypto.keys
import java.security.PublicKey
import java.util.*

data class TrancheState(val tranche: Tranche,
                        val totalAmount: Amount<Issued<Currency>>,
                        val agent: AbstractParty,
                        override val amount: Amount<Issued<Currency>>,
                        override val owner: AbstractParty
) : FungibleAsset<Currency>, QueryableState {

    override val contract get() = TrancheContract()

    override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner, agent)

    /** Tells the vault to track a state if we are one of the parties involved. */
    fun isRelevant(ourKeys: Set<PublicKey>) = ourKeys.intersect(participants.flatMap { it.owningKey.keys }).isNotEmpty()

    override fun withNewOwner(newOwner: AbstractParty) = Pair(TrancheContract.Commands.Move(), copy(owner = newOwner))

    override fun move(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency>
            = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

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
