package com.artie.heart.gateway.tck

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * HEART's conformance features, run against the gateway in front of the
 * lamp's plain JSON twin: the proof that a service known only by its OpenAPI
 * description, and a per-state link to what it allows, is served as HEART.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.heart.gateway.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest
