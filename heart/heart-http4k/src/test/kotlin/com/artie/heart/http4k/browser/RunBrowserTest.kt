package com.artie.heart.http4k.browser

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** The HTML dialect at work in headless Chrome (features/browser): heart-forms.js against the lamps served here. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/browser")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.http4k.browser")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary, message:build/cucumber/RunBrowserTest.ndjson")
class RunBrowserTest
