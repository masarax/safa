package com.safa.account.benchmark

import android.content.Context
import android.net.Uri

/**
 * Requests deterministic data from the target app's benchmark-only provider.
 * The provider is compiled only into the non-production `benchmark` variant,
 * so no test seeding surface exists in debug or release APKs.
 */
object BenchmarkFixture {
    private const val AUTHORITY = "com.safa.account.benchmark-fixture"

    fun seed(context: Context) {
        val result = context.contentResolver.call(
            Uri.parse("content://$AUTHORITY"),
            "seed",
            null,
            null,
        ) ?: error("Benchmark fixture provider did not return a result")

        check(result.getInt("customers") == 400) { "Benchmark customer fixture is incomplete" }
        check(result.getInt("suppliers") == 120) { "Benchmark supplier fixture is incomplete" }
        check(result.getInt("transactions") == 1_200) { "Benchmark transaction fixture is incomplete" }
        check(result.getInt("wallet_batches") == 64) { "Benchmark wallet fixture is incomplete" }
    }
}
