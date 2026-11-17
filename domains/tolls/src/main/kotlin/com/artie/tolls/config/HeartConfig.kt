package com.artie.tolls.config

import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.tolls.declaration.AccountActs
import com.artie.tolls.declaration.Accounts
import com.artie.tolls.domain.ACCOUNTS
import com.artie.tolls.domain.AccountService
import com.artie.tolls.domain.AccountState
import com.artie.tolls.domain.Phase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the account to the HEART library: the declaration, how an account is read, what each act records, how
 * accounts are found, and an account's past. The library serves the rest over HTTP and CoAP.
 */
@Configuration
class HeartConfig {
    @Bean
    fun accounts(service: AccountService): HeartResource<AccountState, Phase> {
        val acts = AccountActs(service)
        return HeartResource(
            layers = Accounts.layers,
            load = { id -> service.state(id) },
            perform = { account, act, input -> Acted(acts.perform(account, act, input)) },
            find = { name, id -> service.find(name, id).map { Found(it.id, it.holder) } },
            past = ACCOUNTS.past(at = { id, instant -> service.state(id, instant) }, history = service::history),
        )
    }
}
