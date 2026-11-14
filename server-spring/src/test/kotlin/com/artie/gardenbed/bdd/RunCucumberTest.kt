package com.artie.gardenbed.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Runs every scenario under src/test/resources/features but Tuesday's
 * (@tuesday, run by RunTuesdayTest on a garden with frost). The bdd-gates
 * logging task in build.gradle.kts reads this class's JUnit XML, so keep
 * the name and package in step with it.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.gardenbed.bdd,com.artie.gardenbed.monday")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @tuesday")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest
