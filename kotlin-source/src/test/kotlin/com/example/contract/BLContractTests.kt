package com.example.contract

import com.example.model.BL
import com.example.state.BLState
import net.corda.testing.*
import org.junit.Test

class BLContractTests {
    @Test
    fun `transaction must include Issue command`() {
        val bl = BL(" "," ")
        ledger {
            transaction {
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                fails()
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { BLContract.Commands.Issue() }
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        val bl = BL(" "," ")
        ledger {
            transaction {
                input { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("No inputs should be consumed when issuing an bl.")
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        val bl = BL(" "," ")
        ledger {
            transaction {
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("Only one output state should be created.")
            }
        }
    }

    @Test
    fun `exporter must sign transaction`() {
        val bl = BL(" "," ")
        ledger {
            transaction {
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MINI_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty must be signers.")
            }
        }
    }

    @Test
    fun `shipping company must sign transaction`() {
        val bl = BL(" ", " ")
        ledger {
            transaction {
                output { BLState(bl, MINI_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty must be signers.")
            }
        }
    }

    @Test
    fun `exporter is not shipping company`() {
        val bl = BL(" ", " ")
        ledger {
            transaction {
                output { BLState(bl, MEGA_CORP, MEGA_CORP, BIG_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("The exporter and the counterParty cannot be the same entity.")
            }
        }
    }

    @Test
    fun `exporter is not importer bank`() {
        val bl = BL(" ", " ")
        ledger {
            transaction {
                output { BLState(bl, MINI_CORP, MEGA_CORP, MINI_CORP, MEGA_CORP) }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { BLContract.Commands.Issue() }
                `fails with`("The exporter and the importerBank cannot be the same entity.")
            }
        }
    }
}