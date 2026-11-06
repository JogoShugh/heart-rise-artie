package com.artie.heart.gateway.bdd

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * The gateway's own features, under src/test/resources/features. The glue
 * includes the conformance package only for its Spring context, which
 * cucumber-spring needs once per run.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.gateway.bdd,com.artie.heart.gateway.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunGatewayFeaturesTest
