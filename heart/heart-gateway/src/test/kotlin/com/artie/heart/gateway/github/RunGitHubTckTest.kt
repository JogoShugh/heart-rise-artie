package com.artie.heart.gateway.github

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * HEART's conformance features, run against the gateway in front of GitHub,
 * known by its own description and the connector that ships with the
 * gateway. GitHub's bookmark offers no act, so the `@bookmark-act`
 * scenarios are left out.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.heart.gateway.github")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @bookmark-act")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunGitHubTckTest
