package com.biblereader.app;

import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Helpers shared by the screen tests. */
final class TestSupport {

    private TestSupport() {
    }

    /** Runs everything queued on the main thread. */
    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Lets background work and delayed tasks finish until the condition holds. */
    static void waitFor(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (true) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** Finds the first descendant TextView showing exactly the given text. */
    static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) {
            return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Lays a list out tall enough that every row exists as a view. */
    static void showAllRows(View list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(40_000, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 600, 40_000);
        idle();
    }
}
