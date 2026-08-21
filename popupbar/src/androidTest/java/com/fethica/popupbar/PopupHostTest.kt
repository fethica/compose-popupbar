package com.fethica.popupbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val HiddenFromAccessibility: SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

@RunWith(AndroidJUnit4::class)
class PopupHostTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: PopupState
    private lateinit var animationScope: CoroutineScope

    private fun setHost(
        interaction: PopupInteractionStyle = PopupInteractionStyle.Drag,
        initial: PopupValue = PopupValue.Collapsed,
        popupBar: @Composable PopupBarScope.() -> Unit = {
            PopupBar(title = "Title", subtitle = "Subtitle")
        },
        popupContent: @Composable PopupContentScope.() -> Unit = {
            Box(Modifier.fillMaxSize().testTag("player"))
        },
    ) {
        rule.setContent {
            state = rememberPopupState(initial)
            animationScope = rememberCoroutineScope()
            MaterialTheme {
                PopupHost(
                    state = state,
                    interactionStyle = interaction,
                    bottomBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .testTag("nav"),
                        )
                    },
                    popupBar = popupBar,
                    popupContent = popupContent,
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .testTag("screenInner"),
                    )
                }
            }
        }
    }

    @Test
    fun tapOnBarExpands() {
        setHost()

        rule.onNodeWithTag("popupbar:bar").performClick()
        rule.waitForIdle()

        assertTrue(state.isExpanded)
    }

    @Test
    fun swipeUpOnBarExpands() {
        setHost()

        rule.onNodeWithTag("popupbar:bar").performTouchInput { swipeUp() }
        rule.waitForIdle()

        assertTrue(state.isExpanded)
    }

    @Test
    fun swipeDownOnContentCollapses() {
        setHost(initial = PopupValue.Expanded)

        rule.onNodeWithTag("popupbar:content").performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertTrue(state.isCollapsed)
    }

    @Test
    fun shortDragSpringsBack() {
        setHost()

        rule.onNodeWithTag("popupbar:bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            up()
        }
        rule.waitForIdle()

        assertTrue(state.isCollapsed)
    }

    @Test
    fun noneIgnoresSwipesButNotTaps() {
        setHost(interaction = PopupInteractionStyle.None)

        rule.onNodeWithTag("popupbar:bar").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue(state.isCollapsed)

        rule.onNodeWithTag("popupbar:bar").performClick()
        rule.waitForIdle()
        assertTrue(state.isExpanded)
    }

    @Test
    fun closeButtonCollapses() {
        setHost(initial = PopupValue.Expanded)

        rule.onNodeWithTag("popupbar:close").performClick()
        rule.waitForIdle()

        assertTrue(state.isCollapsed)
    }

    @Test
    fun presentingGrowsTheScreenInset() {
        setHost(initial = PopupValue.Hidden)
        val hiddenHeight = rule.onNodeWithTag("screenInner").fetchSemanticsNode().size.height

        rule.runOnIdle { animationScope.launch { state.present() } }
        rule.waitForIdle()
        val shownHeight = rule.onNodeWithTag("screenInner").fetchSemanticsNode().size.height

        assertTrue(shownHeight < hiddenHeight)
    }

    @Test
    fun barHasMergedSemantics() {
        setHost()

        rule.onNodeWithTag("popupbar:bar")
            .assertContentDescriptionEquals("Title, Subtitle")
            .assert(hasClickAction())
    }

    @Test
    fun barExposesExactlyOneClickableDescriptionNode() {
        setHost()

        rule.onNodeWithContentDescription("Title, Subtitle").assert(hasClickAction())
        rule.onAllNodesWithContentDescription("Title, Subtitle").assertCountEquals(1)
    }

    /**
     * The seekable strip is a thin band along one edge of the bar, and it has to share that band
     * with two other gestures: the action buttons' 48dp targets reach into it, and a plain tap has
     * to keep expanding the popup. Seeking is drag-only precisely so both stay possible.
     */
    private fun seekVersusActions(style: PopupProgressStyle) {
        var seeked: Float? = null
        var actionClicks = 0
        setHost(
            popupBar = {
                PopupBar(
                    title = "Title",
                    progress = { 0f },
                    onSeek = { seeked = it },
                    progressStyle = style,
                    actions = {
                        IconButton(
                            onClick = { actionClicks++ },
                            modifier = Modifier.testTag("action"),
                        ) { Box(Modifier.size(24.dp)) }
                    },
                )
            },
        )
        val strip = rule.onNodeWithTag("popupbar:progress", useUnmergedTree = true)

        // A horizontal drag along the band seeks, and does nothing else.
        strip.performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertNotNull("a horizontal drag along the band must seek", seeked)
        assertEquals("seeking must not fire the action", 0, actionClicks)
        assertTrue("seeking must not expand the popup", state.isCollapsed)

        // The edge of the action button that overlaps the same band still belongs to the button.
        seeked = null
        val edgeInset = 4f
        rule.onNodeWithTag("action", useUnmergedTree = true).performTouchInput {
            click(
                Offset(
                    width / 2f,
                    if (style == PopupProgressStyle.Top) edgeInset else height - edgeInset,
                ),
            )
        }
        rule.waitForIdle()
        assertEquals("the action button must win inside its own bounds", 1, actionClicks)
        assertNull("pressing the action must not seek", seeked)
        assertTrue(state.isCollapsed)

        // A tap on the band is not a seek: it falls through to the host's tap-to-expand.
        strip.performTouchInput { click() }
        rule.waitForIdle()
        assertNull("a tap on the band must not seek", seeked)
        assertTrue("a tap on the band must expand", state.isExpanded)

        // Neither is a vertical drag: horizontal slop loses, nothing is consumed, and the popup
        // layer's own draggable takes the gesture.
        rule.runOnIdle { animationScope.launch { state.collapse() } }
        rule.waitForIdle()
        // Started on the band but travelling well past it: `swipeUp()` alone only spans the band's
        // own 16dp, which clears neither the positional nor the velocity threshold.
        strip.performTouchInput {
            swipe(start = center, end = center - Offset(0f, 800f), durationMillis = 200)
        }
        rule.waitForIdle()
        assertNull("a vertical swipe from the band must not seek", seeked)
        assertTrue("a vertical swipe from the band must expand", state.isExpanded)
    }

    @Test
    fun topStripSeeksWithoutStealingTheActionButtons() {
        seekVersusActions(PopupProgressStyle.Top)
    }

    @Test
    fun bottomStripSeeksWithoutStealingTheActionButtons() {
        seekVersusActions(PopupProgressStyle.Bottom)
    }

    /**
     * C6: the screen and the docking bar keep being composed and placed behind an expanded popup, so
     * without an explicit gate TalkBack walks straight off the popup into UI the user cannot see.
     *
     * The assertion is about ancestry rather than existence on purpose: `hideFromAccessibility()`
     * marks a subtree for accessibility services but does not prune it from the test's semantics
     * tree, so `assertDoesNotExist()` — which does hold for the `clearAndSetSemantics` case below —
     * would fail here for a reason that has nothing to do with the behaviour under test.
     */
    @Test
    fun expandedPopupHidesTheScreenAndDockingBarFromAccessibility() {
        setHost()

        rule.onNodeWithTag("screenInner").assert(!hasAnyAncestor(HiddenFromAccessibility))
        rule.onNodeWithTag("nav").assert(!hasAnyAncestor(HiddenFromAccessibility))

        rule.runOnIdle { animationScope.launch { state.expand() } }
        rule.waitForIdle()

        rule.onNodeWithTag("screenInner").assert(hasAnyAncestor(HiddenFromAccessibility))
        rule.onNodeWithTag("nav").assert(hasAnyAncestor(HiddenFromAccessibility))
    }

    @Test
    fun collapsedContentIsHiddenFromAccessibility() {
        setHost()

        rule.onNodeWithTag("player").assertDoesNotExist()
    }
}
