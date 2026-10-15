package com.hiennv.flutter_callkit_incoming.widgets

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.hiennv.flutter_callkit_incoming.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi")
class CallSwipeViewTest {
    private lateinit var activity: Activity
    private lateinit var control: CallSwipeView
    private var accepted = 0
    private var declined = 0
    private var eventTime = 0L

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        control = CallSwipeView(activity)
        activity.setContentView(control)
        control.onAccept = { accepted++ }
        control.onDecline = { declined++ }
        control.measure(
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        control.layout(0, 0, control.measuredWidth, control.measuredHeight)
    }

    @Test
    fun tapsOnThumbOrTargetsDoNotAnswerOrDecline() {
        for (id in listOf(R.id.callSwipeThumb, R.id.callSwipeAcceptTarget, R.id.callSwipeDeclineTarget)) {
            val bounds = bounds(id)
            touch(MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
            touch(MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        }
        assertNoAction()
    }

    @Test
    fun shortDragFollowsTheFingerAndReturnsToCentre() {
        val centre = bounds(R.id.callSwipeThumb)
        touch(MotionEvent.ACTION_DOWN, centre.exactCenterX())
        touch(MotionEvent.ACTION_MOVE, centre.exactCenterX() + 30f)
        assertEquals(30f, control.findViewById<View>(R.id.callSwipeThumb).translationX, 0.1f)
        touch(MotionEvent.ACTION_UP, centre.exactCenterX() + 30f)
        settle()
        assertEquals(0f, control.findViewById<View>(R.id.callSwipeThumb).translationX, 0.1f)
        assertNoAction()
    }

    @Test
    fun rightDragAnswersOnceOnlyAfterRelease() {
        touch(MotionEvent.ACTION_DOWN, bounds(R.id.callSwipeThumb).exactCenterX())
        touch(MotionEvent.ACTION_MOVE, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        assertNoAction()
        touch(MotionEvent.ACTION_UP, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        touch(MotionEvent.ACTION_UP, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        assertEquals(1, accepted)
        assertEquals(0, declined)
    }

    @Test
    fun leftDragDeclinesWithoutAnswering() {
        dragTo(R.id.callSwipeDeclineTarget, MotionEvent.ACTION_UP)
        assertEquals(0, accepted)
        assertEquals(1, declined)
    }

    @Test
    fun cancelledDragAtEitherTargetDoesNotResolveTheCall() {
        for (target in listOf(R.id.callSwipeAcceptTarget, R.id.callSwipeDeclineTarget)) {
            dragTo(target, MotionEvent.ACTION_CANCEL)
            settle()
        }
        assertNoAction()
        assertEquals(0f, control.findViewById<View>(R.id.callSwipeThumb).translationX, 0.1f)
    }

    @Test
    fun draggingBackBeforeReleaseDoesNotAnswer() {
        val start = bounds(R.id.callSwipeThumb).exactCenterX()
        touch(MotionEvent.ACTION_DOWN, start)
        touch(MotionEvent.ACTION_MOVE, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        touch(MotionEvent.ACTION_UP, start + 15f)
        assertNoAction()
    }

    @Test
    fun leavingTheTrackCancelsTheGesture() {
        touch(MotionEvent.ACTION_DOWN, bounds(R.id.callSwipeThumb).exactCenterX())
        touch(MotionEvent.ACTION_MOVE, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        touch(MotionEvent.ACTION_MOVE, bounds(R.id.callSwipeAcceptTarget).exactCenterX(), 180f)
        touch(MotionEvent.ACTION_UP, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        assertNoAction()
    }

    @Test
    fun dragMustStartOnTheCentreThumb() {
        val target = bounds(R.id.callSwipeAcceptTarget).exactCenterX()
        touch(MotionEvent.ACTION_DOWN, target)
        touch(MotionEvent.ACTION_MOVE, target - 20f)
        touch(MotionEvent.ACTION_UP, target)
        assertNoAction()
    }

    @Test
    fun secondFingerCancelsTheDrag() {
        touch(MotionEvent.ACTION_DOWN, bounds(R.id.callSwipeThumb).exactCenterX())
        touch(MotionEvent.ACTION_MOVE, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        touch(MotionEvent.ACTION_POINTER_DOWN, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        touch(MotionEvent.ACTION_UP, bounds(R.id.callSwipeAcceptTarget).exactCenterX())
        assertNoAction()
    }

    @Test
    fun talkBackCanAnswerOnceThroughAnExplicitAction() {
        assertTrue(control.performAccessibilityAction(R.id.callSwipeAnswerAction, null))
        assertFalse(control.performAccessibilityAction(R.id.callSwipeDeclineAction, null))
        assertEquals(1, accepted)
        assertEquals(0, declined)
    }

    @Test
    fun talkBackCanDeclineThroughAnExplicitAction() {
        assertTrue(control.performAccessibilityAction(R.id.callSwipeDeclineAction, null))
        assertEquals(0, accepted)
        assertEquals(1, declined)
    }

    @Test
    fun bothScreenLayoutsUseTheSwipeControl() {
        for (qualifiers in listOf("w360dp-h640dp-port-mdpi", "w960dp-h600dp-land-mdpi")) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            val root = LayoutInflater.from(activity).inflate(R.layout.activity_callkit_incoming, null)
            assertNotNull(root.findViewById<CallSwipeView>(R.id.callSwipe))
            assertNotNull(root.findViewById<View>(R.id.ivAvatar))
            assertNotNull(root.findViewById<View>(R.id.tvNameCaller))
        }
    }

    private fun bounds(id: Int): Rect = Rect().also {
        val child = control.findViewById<View>(id)
        child.getDrawingRect(it)
        control.offsetDescendantRectToMyCoords(child, it)
    }

    private fun touch(action: Int, x: Float, y: Float = bounds(R.id.callSwipeTrack).exactCenterY()) {
        val event = MotionEvent.obtain(0L, ++eventTime, action, x, y, 0)
        control.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun dragTo(target: Int, release: Int) {
        touch(MotionEvent.ACTION_DOWN, bounds(R.id.callSwipeThumb).exactCenterX())
        touch(MotionEvent.ACTION_MOVE, bounds(target).exactCenterX())
        touch(release, bounds(target).exactCenterX())
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))

    private fun assertNoAction() {
        assertEquals(0, accepted)
        assertEquals(0, declined)
    }
}
