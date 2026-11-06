package com.artie.heart.tck.lamp

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * The lamp runs HEART's conformance features: the proof that a server which
 * only declares a type, and writes no HTTP code, is HEART compliant. The
 * bdd-gates log task reads this class's JUnit XML.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest
