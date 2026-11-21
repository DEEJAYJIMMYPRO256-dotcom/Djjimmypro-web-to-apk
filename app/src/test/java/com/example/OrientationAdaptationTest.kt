package com.example

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.dj.ui.DjScreen
import com.example.dj.ui.DjViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrientationAdaptationTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `MainActivity has no fixed orientation lock and handles configuration change`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()

    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

    val landscapeConfig =
      Configuration(activity.resources.configuration).apply {
        orientation = Configuration.ORIENTATION_LANDSCAPE
      }
    activity.onConfigurationChanged(landscapeConfig)

    val portraitConfig =
      Configuration(activity.resources.configuration).apply {
        orientation = Configuration.ORIENTATION_PORTRAIT
      }
    activity.onConfigurationChanged(portraitConfig)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
  fun `DjScreen renders portrait dual-deck layout on portrait screen`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent {
      DjScreen(viewModel = vm)
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("layout_portrait_dual_deck").assertIsDisplayed()
    composeTestRule.onNodeWithTag("play_button_A").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w844dp-h390dp-land")
  fun `DjScreen adapts fluidly to landscape dual-deck layout on landscape screen`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent {
      DjScreen(viewModel = vm)
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("layout_landscape_dual_deck").assertIsDisplayed()
    composeTestRule.onNodeWithTag("play_button_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("play_button_B").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
  fun `DjScreen preserves rememberSaveable UI state across configuration state restoration`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)
    val restorationTester = StateRestorationTester(composeTestRule)

    restorationTester.setContent {
      DjScreen(viewModel = vm)
    }
    composeTestRule.waitForIdle()

    // Open Track Library sheet for Deck A (saved via rememberSaveable)
    composeTestRule.onNodeWithTag("open_library_A").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("library_search_bar").assertIsDisplayed()

    // Simulate configuration change / saved instance state restoration
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("library_search_bar").assertIsDisplayed()
  }
}
