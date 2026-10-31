package com.artie.heart.spring.bdd

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Runs heart-tck's HTTP binding features (classpath heart/binding) against the
 * lamp served by heart-spring. The bdd-gates log task reads its JUnit XML.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/binding")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.binding,com.artie.heart.spring.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest
