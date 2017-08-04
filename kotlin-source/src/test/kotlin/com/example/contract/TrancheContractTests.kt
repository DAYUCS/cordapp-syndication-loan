package com.example.contract

import com.example.model.Tranche
import com.example.state.TrancheState
import net.corda.testing.*
import org.junit.Test

class TrancheContractTests {
    @Test
    fun `transaction must include Issue command`() {
        val bl = Tranche(" "," ")
        ledger {
            transaction {
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                fails()
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        val bl = Tranche(" "," ")
        ledger {
            transaction {
                input { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("No inputs should be consumed when issuing an tranche.")
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        val bl = Tranche(" "," ")
        ledger {
            transaction {
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("Only one output state should be created.")
            }
        }
    }

    @Test
    fun `exporter must sign transaction`() {
        val bl = Tranche(" "," ")
        ledger {
            transaction {
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MINI_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty must be signers.")
            }
        }
    }

    @Test
    fun `shipping company must sign transaction`() {
        val bl = Tranche(" ", " ")
        ledger {
            transaction {
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty must be signers.")
            }
        }
    }

    @Test
    fun `exporter is not shipping company`() {
        val bl = Tranche(" ", " ")
        ledger {
            transaction {
                output { TrancheState(bl, MEGA_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty cannot be the same entity.")
            }
        }
    }

    @Test
    fun `exporter is not importer bank`() {
        val bl = Tranche(" ", " ")
        ledger {
            transaction {
                output { TrancheState(bl, MINI_CORP, MEGA_CORP, MINI_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { TrancheContract.Commands.Issue() }
                `fails with`("The exporter and the importerBank cannot be the same entity.")
            }
        }
    }
}