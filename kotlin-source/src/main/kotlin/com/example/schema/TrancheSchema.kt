package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object TrancheSchema

object TrancheSchemaV1 : MappedSchema(
        schemaFamily = TrancheSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTranche::class.java)) {
    @Entity
    @Table(name = "tranche_states")
    class PersistentTranche(
            @Column(name = "reference_no")
            var referenceNumber: String,

            @Column(name = "borrower")
            var borrower: String,

            @Column(name = "interest_rate")
            var interestRate: String,

            @Column(name = "exchange_rate")
            var exchangeRate:String,

            @Column(name= "ir_fixing_date")
            var irFixingDate: String,

            @Column(name = "er_fixing_date")
            var erFixingDate: String,

            @Column(name = "start_date")
            var startDate: String,

            @Column(name = "end_date")
            var endDate: String,

            @Column(name = "tx_currency")
            var txCurrency: String

    ) : PersistentState()
}