package com.example.state

import com.example.contract.BLContract
import com.example.model.BL
import com.example.schema.BLSchemaV1
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.crypto.keys
import java.security.PublicKey

/**
 * The state object recording bl agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param bl details of the bl.
 * @param exporter the party issuing the bl.
 * @param shippingCompany the party receiving and approving the bl.
 * @param importerBank the counterParty's bank.
 * @param owner the party who hold the bl.
 */
data class BLState(val bl: BL,
                   val exporter: Party,
                   val shippingCompany: Party,
                   val importerBank: Party,
                   val owner: Party,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()
                   ): LinearState, QueryableState {

    override val contract get() = BLContract()

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    /** Tells the vault to track a state if we are one of the parties involved. */
    override fun isRelevant(ourKeys: Set<PublicKey>) = ourKeys.intersect(participants.flatMap { it.owningKey.keys }).isNotEmpty()

    fun withNewOwner(newOwner: Party) = copy(owner = newOwner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is BLSchemaV1 -> BLSchemaV1.PersistentBL(
                    exporterName = this.exporter.name.toString(),
                    shippingCompany = this.shippingCompany.name.toString(),
                    importerBank = this.importerBank.name.toString(),
                    owner = this.owner.name.toString(),
                    referenceNumber = this.bl.referenceNumber,
                    packingList = this.bl.packingList
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(BLSchemaV1)
}
