package com.invapp.app.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OnboardingPrefsTest {

    @Test
    public void coldStartShowsUntilSkippedOrCompleted() {
        android.content.Context ctx = RuntimeEnvironment.getApplication();
        OnboardingPrefs.reset(ctx);
        assertTrue(OnboardingPrefs.shouldShowOnColdStart(ctx));

        OnboardingPrefs.markSkipped(ctx);
        assertFalse(OnboardingPrefs.shouldShowOnColdStart(ctx));
        assertTrue(OnboardingPrefs.isFinished(ctx));

        OnboardingPrefs.reset(ctx);
        OnboardingPrefs.markCompleted(ctx);
        assertFalse(OnboardingPrefs.shouldShowOnColdStart(ctx));
        assertTrue(OnboardingPrefs.isFinished(ctx));
    }

    @Test
    public void stepClamps() {
        android.content.Context ctx = RuntimeEnvironment.getApplication();
        OnboardingPrefs.reset(ctx);
        OnboardingPrefs.setStep(ctx, 99);
        assertEquals(OnboardingPrefs.STEP_AI, OnboardingPrefs.getStep(ctx));
        OnboardingPrefs.setStep(ctx, -3);
        assertEquals(OnboardingPrefs.STEP_STORAGE, OnboardingPrefs.getStep(ctx));
    }
}
