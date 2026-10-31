package com.artie.heart.http4k.binding

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * The HTTP binding features from heart-tck (classpath heart/binding), run
 * against the lamp served by heart-http4k on a real port.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/binding")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.binding,com.artie.heart.http4k.binding")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunBindingTest
