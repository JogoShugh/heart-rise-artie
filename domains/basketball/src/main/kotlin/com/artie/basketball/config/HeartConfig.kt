package com.artie.basketball.config

import com.artie.basketball.declaration.GameActs
import com.artie.basketball.declaration.Games
import com.artie.basketball.domain.GAMES
import com.artie.basketball.domain.GameService
import com.artie.basketball.domain.GameState
import com.artie.basketball.domain.Phase
import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the game to the HEART library: the declaration, how a game is read,
 * what each act records, how games are found, and a game's past. The library
 * serves the rest over HTTP and CoAP.
 */
@Configuration
class HeartConfig {
    @Bean
    fun games(service: GameService): HeartResource<GameState, Phase> {
        val acts = GameActs(service)
        return HeartResource(
            layers = Games.layers,
            load = { id -> service.state(id) },
            perform = { game, act, input -> Acted(acts.perform(game, act, input)) },
            find = { name, id -> service.find(name, id).map { Found(it.id, it.label) } },
            past = GAMES.past(at = { id, instant -> service.state(id, instant) }, history = service::history),
        )
    }
}
