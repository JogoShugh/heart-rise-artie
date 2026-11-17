package com.artie.tolls.store

import com.artie.tolls.domain.AccountDirectory
import com.artie.tolls.domain.AccountSetup
import kotlinx.coroutines.flow.toList
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import java.util.concurrent.ConcurrentHashMap

/** Accounts set up in memory: for tests and demos. */
class InMemoryAccountDirectory : AccountDirectory {
    private val accounts = ConcurrentHashMap<Long, AccountSetup>()

    fun add(account: AccountSetup) {
        accounts[account.id] = account
    }

    fun clear() = accounts.clear()

    override suspend fun find(id: Long): AccountSetup? = accounts[id]

    override suspend fun all(): List<AccountSetup> = accounts.values.toList()
}

/** Accounts set up in Postgres: table `account` (migration V1). */
class PostgresAccountDirectory(private val db: DatabaseClient) : AccountDirectory {

    override suspend fun find(id: Long): AccountSetup? = all().firstOrNull { it.id == id }

    override suspend fun all(): List<AccountSetup> =
        db.sql("SELECT id, holder FROM account ORDER BY id")
            .map { r -> AccountSetup((r.get("id") as Number).toLong(), r.get("holder") as String) }
            .flow().toList()
}
