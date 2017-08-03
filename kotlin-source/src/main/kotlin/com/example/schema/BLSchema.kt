package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object BLSchema

object BLSchemaV1 : MappedSchema(
        schemaFamily = BLSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentBL::class.java)) {
    @Entity
    @Table(name = "bl_states")
    class PersistentBL(
            @Column(name = "exporter_name")
            var exporterName: String,

            @Column(name = "shipping_company")
            var shippingCompany: String,

            @Column(name = "importer_bank")
            var importerBank: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "reference_no")
            var referenceNumber: String,

            @Column(name = "packing_list")
            var packingList:String
    ) : PersistentState()
}