package com.example.flow

import com.example.model.BL
import com.example.state.BLState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowSessionException
import net.corda.core.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BLFlowTests {
    lateinit var net: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var c: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        nodes.partyNodes.forEach { it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java) }
        net.runNetwork()
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    @Test
    fun `flow rejects invalid BLs`() {
        val state = BLState(
                BL("",""),
                a.info.legalIdentity,
                b.info.legalIdentity,
                a.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        // The BLContract specifies that bl's exporter and importerbank cannot be the same.
        assertFailsWith<TransactionVerificationException> {future.getOrThrow()}
    }

    @Test
    fun `flow rejects invalid BL states`() {
        val state = BLState(
                BL("",""),
                a.info.legalIdentity,
                a.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        // The BLContract specifies that an bl's exporter and counterParty cannot be the same.
        assertFailsWith<TransactionVerificationException> {future.getOrThrow()}
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the exporter`() {
        val state = BLState(
                BL("BL0001","abc 10"),
                a.info.legalIdentity,
                b.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignatures(a.services.legalIdentityKey)
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the shipping company`() {
        val state = BLState(
                BL("",""),
                a.info.legalIdentity,
                b.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignatures(b.services.legalIdentityKey)
    }

    @Test
    fun `flow rejects BLs that are not signed by the exporter`() {
        val state = BLState(
                BL("",""),
                c.info.legalIdentity,
                b.info.legalIdentity,
                a.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        assertFailsWith<FlowSessionException> { future.getOrThrow() }
    }

    @Test
    fun `flow rejects BLs that are not signed by the shipping company`() {
        val state = BLState(
                BL("",""),
                a.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity,
                c.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        assertFailsWith<FlowSessionException> { future.getOrThrow() }
    }

    @Test
    fun `flow records a transaction in both parties' vaults`() {
        val state = BLState(
                BL("",""),
                a.info.legalIdentity,
                b.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(state, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.storage.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the input BL`() {
        val inputState = BLState(
                BL("",""),
                a.info.legalIdentity,
                b.info.legalIdentity,
                c.info.legalIdentity,
                b.info.legalIdentity)
        val flow = ExampleFlow.Initiator(inputState, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.storage.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as BLState
            assertEquals(recordedState.bl, inputState.bl)
            assertEquals(recordedState.exporter, inputState.exporter)
            assertEquals(recordedState.shippingCompany, inputState.shippingCompany)
            assertEquals(recordedState.importerBank, inputState.importerBank)
            assertEquals(recordedState.owner, inputState.owner)
        }
    }
}