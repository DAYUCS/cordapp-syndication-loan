package com.example.model

import net.corda.core.serialization.CordaSerializable

/**
 * A simple class representing an bl.
 *
 * This is the data structure that the parties will reach agreement over. These data structures can be arbitrarily
 * complex. See https://github.com/corda/corda/blob/master/samples/irs-demo/src/main/kotlin/net/corda/irs/contract/IRS.kt
 * for a more complicated example.
 *
 * @param value the bl's value.
 */
@CordaSerializable
data class BL(val referenceNumber: String, val packingList: String)