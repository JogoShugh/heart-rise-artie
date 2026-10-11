package com.artie.heart.spring

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

/**
 * Turns a server's declared resource types (its [com.artie.heart.Layers]
 * beans) into HEART over HTTP. Spring Boot applies it from the library's
 * `AutoConfiguration.imports`; a test slice imports it by name.
 */
@AutoConfiguration
@Import(HeartRegistry::class, HeartContentController::class, HeartProfileController::class)
class HeartAutoConfiguration
