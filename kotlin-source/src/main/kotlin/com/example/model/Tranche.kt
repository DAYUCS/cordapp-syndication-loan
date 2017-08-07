package com.example.model

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Tranche(val referenceNumber: String, val borrower: String, val interestRate: String,
                   val exchangeRate: String, val irFixingDate: String, val erFixingDate: String,
                   val startDate: String, val endDate: String, val txCurrency: String)