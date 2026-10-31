package com.artie.heart.http4k.tck

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * HEART's conformance features, run against the lamp served by heart-http4k
 * over a real port: the proof that the http4k binding serves a declaration
 * exactly as heart-spring does.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.heart.http4k.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest
