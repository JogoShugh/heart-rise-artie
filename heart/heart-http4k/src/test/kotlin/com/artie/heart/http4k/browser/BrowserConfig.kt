package com.artie.heart.http4k.browser

import com.artie.heart.http4k.binding.LampBinding
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.test.context.ContextConfiguration

/** The browser features' server: the lamps heart-http4k serves on a real port, as the binding features have them. */
@CucumberContextConfiguration
@ContextConfiguration(classes = [LampBinding::class])
class BrowserConfig
