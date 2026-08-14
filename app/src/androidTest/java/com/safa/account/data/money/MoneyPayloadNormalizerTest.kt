package com.safa.account.data.money

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyPayloadNormalizerTest {
    @Test fun nestedSyncPayloadUsesExactDecimalStrings() {
        val raw = """{"transactions":[{"amount_sar":0.30000000000000004,"customer_rate":32.12345,"supplier_rate":"32","amount_bdt":9.637}]}"""
        val normalized = MoneyPayloadNormalizer.normalizeJson(raw)!!
        val tx = JSONObject(normalized).getJSONArray("transactions").getJSONObject(0)

        assertEquals("0.30", tx.getString("amount_sar"))
        assertEquals("32.1235", tx.getString("customer_rate"))
        assertEquals("32.0000", tx.getString("supplier_rate"))
        assertEquals("9.64", tx.getString("amount_bdt"))
    }

    @Test fun repeatedNormalizationDoesNotChangeCanonicalMoney() {
        val once = MoneyPayloadNormalizer.normalizeJson("""{"amount_sar":"10.005","customer_rate":"32.12345","amount_bdt":"321.234"}""")!!
        val twice = MoneyPayloadNormalizer.normalizeJson(once)!!
        assertEquals(once, twice)
        val json = JSONObject(twice)
        assertEquals("10.01", json.getString("amount_sar"))
        assertEquals("32.1235", json.getString("customer_rate"))
        assertEquals("321.23", json.getString("amount_bdt"))
    }

    @Test fun nonFinancialNumbersRemainNumeric() {
        val normalized = MoneyPayloadNormalizer.normalizeJson("""{"local_id":101,"customer_id":7,"amount_sar":10}""")!!
        val json = JSONObject(normalized)
        assertEquals(101, json.getInt("local_id"))
        assertEquals(7, json.getInt("customer_id"))
        assertEquals("10.00", json.getString("amount_sar"))
    }

    @Test fun signedSarAdjustmentIsKeptButNegativeBdtIsRejected() {
        val signed = MoneyPayloadNormalizer.normalizeJson("""{"sar_collected":"-0.105","bdt_disbursed":"1"}""")!!
        assertEquals("-0.11", JSONObject(signed).getString("sar_collected"))
        assertThrows(IllegalArgumentException::class.java) {
            MoneyPayloadNormalizer.normalizeJson("""{"bdt_disbursed":"-0.01"}""")
        }
    }
}
