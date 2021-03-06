package com.mopub.mobileads;

import android.app.Activity;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mopub.common.AdType;
import com.mopub.common.DataKeys;
import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPubReward;
import com.mopub.common.test.support.SdkTestRunner;
import com.mopub.network.AdRequest;
import com.mopub.network.AdResponse;
import com.mopub.network.MoPubRequestQueue;
import com.mopub.network.Networking;
import com.mopub.volley.Request;
import com.mopub.volley.VolleyError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Map;
import java.util.Set;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(SdkTestRunner.class)
@Config(constants = BuildConfig.class)
public class
        MoPubRewardedVideoManagerTest {

    public static final String MOPUB_REWARD = "mopub_reward";

    @Mock
    MoPubRequestQueue mockRequestQueue;
    @Mock
    MoPubRewardedVideoListener mockVideoListener;

    AdRequest.Listener requestListener;
    private AdRequest request;
    private Activity mActivity;

    @Before
    public void setup() {
        mActivity = Robolectric.buildActivity(Activity.class).create().get();
        MoPubRewardedVideoManager.init(mActivity);
        MoPubRewardedVideoManager.setVideoListener(mockVideoListener);

        when(mockRequestQueue.add(any(AdRequest.class))).then(new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                request = ((AdRequest) invocationOnMock.getArguments()[0]);
                requestListener = request.getListener();
                return null;
            }
        });

        Networking.setRequestQueueForTesting(mockRequestQueue);
    }

    @After
    public void tearDown() {
        // Unpause the main looper in case a test terminated while the looper was paused.
        ShadowLooper.unPauseMainLooper();
        MoPubRewardedVideoManager.getRewardedVideoData().clear();
    }

    @Test
    public void loadVideo_withRequestParameters_shouldGenerateUrlWithKeywords() {
        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", new MoPubRewardedVideoManager.RequestParameters("nonsense;garbage;keywords"));

        verify(mockRequestQueue).add(argThat(new RequestUrlContains(Uri.encode("nonsense;garbage;keywords"))));

        // Finish the request
        requestListener.onErrorResponse(new VolleyError("end test"));
        ShadowLooper.unPauseMainLooper();
    }

    @Test
    public void loadVideo_withCustomerIdInRequestParameters_shouldSetCustomerId() {
        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", new MoPubRewardedVideoManager.RequestParameters("keywords", null, "testCustomerId"));

        assertThat(MoPubRewardedVideoManager.getRewardedVideoData().getCustomerId()).isEqualTo("testCustomerId");

        // Finish the request
        requestListener.onErrorResponse(new VolleyError("end test"));
        ShadowLooper.unPauseMainLooper();
    }

    @Test
    public void loadVideo_withVideoAlreadyShowing_shouldNotLoadVideo() {
        // To simulate that a video is showing
        MoPubRewardedVideoManager.getRewardedVideoData().setCurrentlyShowingAdUnitId("testAdUnit");

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);

        ShadowLooper.unPauseMainLooper();

        verifyZeroInteractions(mockRequestQueue);
    }

    @Test
    public void callbackMethods_withNullListener_shouldNotError() {
        // Clients can set RVM null.
        MoPubRewardedVideoManager.setVideoListener(null);

        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName("com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        // Triggers a call to MoPubRewardedVideoManager.onRewardedVideoLoadSuccess
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        MoPubRewardedVideoManager.onRewardedVideoClicked(TestCustomEvent.class,
                TestCustomEvent.AD_NETWORK_ID);
        MoPubRewardedVideoManager.onRewardedVideoStarted(TestCustomEvent.class,
                TestCustomEvent.AD_NETWORK_ID);
        MoPubRewardedVideoManager.onRewardedVideoClosed(TestCustomEvent.class,
                TestCustomEvent.AD_NETWORK_ID);
        MoPubRewardedVideoManager.onRewardedVideoCompleted(TestCustomEvent.class,
                TestCustomEvent.AD_NETWORK_ID,
                MoPubReward.success("test", 111));

        // The test passed because none of the above calls thew an exception even though the listener is null.
    }

    @Test
    public void onAdSuccess_noActivityFound_shouldNotCallFailUrl() {
        AdResponse testResponse = new AdResponse.Builder()
                .setAdType(AdType.CUSTOM)
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setFailoverUrl("fail.url")
                .build();

        MoPubRewardedVideoManager.updateActivity(null);
        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        verify(mockRequestQueue).add(any(AdRequest.class));
        verifyNoMoreInteractions(mockRequestQueue);

        // Clean up the static state we screwed up:
        MoPubRewardedVideoManager.updateActivity(mActivity);
    }

    @Test
    public void onAdSuccess_noCEFound_shouldCallFailCallback() throws Exception {
        AdResponse testResponse = new AdResponse.Builder()
                .setAdType(AdType.CUSTOM)
                .setCustomEventClassName("doesn't_Exist")
                .build();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);

        requestListener.onSuccess(testResponse);

        verify(mockVideoListener).onRewardedVideoLoadFailure(eq("testAdUnit"),
                eq(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR));
        verifyNoMoreInteractions(mockVideoListener);
    }

    @Test
    public void onAdSuccess_noCEFound_shouldLoadFailUrl() {
        AdResponse testResponse = new AdResponse.Builder()
                .setAdType(AdType.CUSTOM)
                .setCustomEventClassName("doesn't_Exist")
                .setFailoverUrl("fail.url")
                .build();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);

        assertThat(request.getUrl()).contains("testAdUnit");
        requestListener.onSuccess(testResponse);
        assertThat(request.getUrl()).isEqualTo("fail.url");
        // Clear up the static state :(
        requestListener.onErrorResponse(new VolleyError("reset"));
    }

    @Test
    public void onAdSuccess_shouldInstantiateCustomEvent_andLoad() {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isTrue();
        verify(mockVideoListener).onRewardedVideoLoadSuccess(eq("testAdUnit"));
        verifyNoMoreInteractions(mockVideoListener);
    }

    @Test
    public void onAdSuccess_withCustomEventAlreadyLoaded_shouldInvalidateOldCustomEvent() throws Exception {
        final CustomEventRewardedVideo mockCustomEvent = mock(CustomEventRewardedVideo.class);
        MoPubRewardedVideoManager.getRewardedVideoData().updateAdUnitCustomEventMapping(
                "testAdUnit", mockCustomEvent, null, TestCustomEvent.AD_NETWORK_ID);

        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        // Load the first custom event
        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        // Verify the first custom event
        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isTrue();
        verify(mockVideoListener).onRewardedVideoLoadSuccess(eq("testAdUnit"));
        verifyNoMoreInteractions(mockVideoListener);
        reset(mockVideoListener);

        ShadowLooper.pauseMainLooper();

        // Load the second custom event
        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        // Verify the second custom event was loaded
        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isTrue();
        verify(mockVideoListener).onRewardedVideoLoadSuccess(eq("testAdUnit"));
        verifyNoMoreInteractions(mockVideoListener);

        // Verify that the first custom event was invalidated
        verify(mockCustomEvent).onInvalidate();
        verifyNoMoreInteractions(mockCustomEvent);
    }

    @Test
    public void onAdSuccess_shouldHaveUniqueBroadcastIdsSetForEachCustomEvent() throws Exception {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        // Load the first custom event
        MoPubRewardedVideoManager.loadVideo("testAdUnit1", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        // Get the first custom event's broadcast id
        TestCustomEvent testCustomEvent1 = (TestCustomEvent)
                MoPubRewardedVideoManager.getRewardedVideoData().getCustomEvent("testAdUnit1");
        Long broadcastId1 = (Long) testCustomEvent1.getLocalExtras().get(
                DataKeys.BROADCAST_IDENTIFIER_KEY);
        assertThat(broadcastId1).isNotNull();

        ShadowLooper.pauseMainLooper();

        // Load the second custom event
        MoPubRewardedVideoManager.loadVideo("testAdUnit2", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        // Get the second custom event's broadcast id
        TestCustomEvent testCustomEvent2 = (TestCustomEvent)
                MoPubRewardedVideoManager.getRewardedVideoData().getCustomEvent("testAdUnit2");
        Long broadcastId2 = (Long) testCustomEvent2.getLocalExtras().get(
                DataKeys.BROADCAST_IDENTIFIER_KEY);
        assertThat(broadcastId2).isNotNull();

        // Make sure they're different
        assertThat(broadcastId1).isNotEqualTo(broadcastId2);
    }

    @Test
    public void onAdSuccess_shouldUpdateAdUnitRewardMapping() throws Exception {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .setRewardedVideoCurrencyName("currency_name")
                .setRewardedVideoCurrencyAmount("123")
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        MoPubReward moPubReward =
                MoPubRewardedVideoManager.getRewardedVideoData().getMoPubReward("testAdUnit");
        assertThat(moPubReward.getAmount()).isEqualTo(123);
        assertThat(moPubReward.getLabel()).isEqualTo("currency_name");
    }

    @Test
    public void playVideo_shouldSetHasVideoFalse() {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName("com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isTrue();
        MoPubRewardedVideoManager.showVideo("testAdUnit");
        verify(mockVideoListener).onRewardedVideoLoadSuccess(eq("testAdUnit"));
        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isFalse();
        verify(mockVideoListener).onRewardedVideoStarted(eq("testAdUnit"));
    }
    
    @Test
    public void playVideo_whenNotHasVideo_shouldFail() {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName("com.mopub.mobileads.MoPubRewardedVideoManagerTest$NoVideoCustomEvent")
                .setAdType(AdType.CUSTOM)
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        verify(mockVideoListener).onRewardedVideoLoadFailure(eq("testAdUnit"), eq(MoPubErrorCode.NETWORK_NO_FILL));

        assertThat(MoPubRewardedVideoManager.hasVideo("testAdUnit")).isFalse();
        MoPubRewardedVideoManager.showVideo("testAdUnit");
        verify(mockVideoListener).onRewardedVideoLoadFailure(eq("testAdUnit"), eq(MoPubErrorCode.VIDEO_NOT_AVAILABLE));
    }

    @Test
    public void playVideo_shouldUpdateLastShownCustomEventRewardMapping() throws Exception {
        AdResponse testResponse = new AdResponse.Builder()
                .setCustomEventClassName(
                        "com.mopub.mobileads.MoPubRewardedVideoManagerTest$TestCustomEvent")
                .setAdType(AdType.CUSTOM)
                .setRewardedVideoCurrencyName("currency_name")
                .setRewardedVideoCurrencyAmount("123")
                .build();

        // Robolectric executes its handlers immediately, so if we want the async behavior we see
        // in an actual app we have to pause the main looper until we're done successfully loading the ad.
        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);
        requestListener.onSuccess(testResponse);

        ShadowLooper.unPauseMainLooper();

        MoPubRewardedVideoManager.showVideo("testAdUnit");

        MoPubReward moPubReward =
                MoPubRewardedVideoManager.getRewardedVideoData().getLastShownMoPubReward(TestCustomEvent.class);
        assertThat(moPubReward.getAmount()).isEqualTo(123);
        assertThat(moPubReward.getLabel()).isEqualTo("currency_name");
    }

    @Test
    public void onAdFailure_shouldCallFailCallback() {
        VolleyError e = new VolleyError("testError!");

        MoPubRewardedVideoManager.loadVideo("testAdUnit", null);

        assertThat(request.getUrl()).contains("testAdUnit");
        requestListener.onErrorResponse(e);
        verify(mockVideoListener).onRewardedVideoLoadFailure(anyString(), any(MoPubErrorCode.class));
        verifyNoMoreInteractions(mockVideoListener);
    }

    @Test
    public void chooseReward_shouldReturnMoPubRewardOverNetworkReward() throws Exception {
        MoPubReward moPubReward = MoPubReward.success(MOPUB_REWARD, 123);
        MoPubReward networkReward = MoPubReward.success("network_reward", 456);

        MoPubReward chosenReward =
                MoPubRewardedVideoManager.chooseReward(moPubReward, networkReward);
        assertThat(chosenReward).isEqualTo(moPubReward);
    }

    @Test
    public void chooseReward_withNetworkRewardNotSuccessful_shouldReturnNetworkReward() throws Exception {
        MoPubReward moPubReward = MoPubReward.success(MOPUB_REWARD, 123);
        MoPubReward networkReward = MoPubReward.failure();

        MoPubReward chosenReward =
                MoPubRewardedVideoManager.chooseReward(moPubReward, networkReward);
        assertThat(chosenReward).isEqualTo(networkReward);
    }
    
    @Test
    public void onRewardedVideoCompleted_withEmptyServerCompletionUrl_withCurrentlyShowingAdUnitId_shouldNotifyRewardedVideoCompletedForOneAdUnitId() {
        MoPubReward moPubReward = MoPubReward.success(MOPUB_REWARD, 123);
        RewardedVideoData rewardedVideoData = MoPubRewardedVideoManager.getRewardedVideoData();
        rewardedVideoData.setCurrentlyShowingAdUnitId("testAdUnit1");
        rewardedVideoData.updateAdUnitCustomEventMapping("testAdUnit1", new TestCustomEvent(), null,
                TestCustomEvent.AD_NETWORK_ID);
        rewardedVideoData.updateAdUnitCustomEventMapping("testAdUnit2", new TestCustomEvent(), null,
                TestCustomEvent.AD_NETWORK_ID);
        // Server completion url empty and custom event has no server reward set

        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.onRewardedVideoCompleted(TestCustomEvent.class, TestCustomEvent.AD_NETWORK_ID,
                moPubReward);
        
        ShadowLooper.unPauseMainLooper();

        ArgumentCaptor<Set<String>> rewardedIdsCaptor = ArgumentCaptor.forClass((Class) Set.class);
        verify(mockVideoListener).onRewardedVideoCompleted(rewardedIdsCaptor.capture(),
                eq(moPubReward));
        assertThat(rewardedIdsCaptor.getValue()).containsOnly("testAdUnit1");
    }

    @Test
    public void onRewardedVideoCompleted_withEmptyServerCompletionUrl_withNoCurrentlyShowingAdUnitId_shouldNotifyRewardedVideoCompletedForAllAdUnitIds() {
        MoPubReward moPubReward = MoPubReward.success(MOPUB_REWARD, 123);
        RewardedVideoData rewardedVideoData = MoPubRewardedVideoManager.getRewardedVideoData();
        rewardedVideoData.setCurrentlyShowingAdUnitId(null);
        rewardedVideoData.updateAdUnitCustomEventMapping("testAdUnit1", new TestCustomEvent(), null,
                TestCustomEvent.AD_NETWORK_ID);
        rewardedVideoData.updateAdUnitCustomEventMapping("testAdUnit2", new TestCustomEvent(), null,
                TestCustomEvent.AD_NETWORK_ID);
        rewardedVideoData.updateAdUnitCustomEventMapping("testAdUnit3", new TestCustomEvent(), null,
                TestCustomEvent.AD_NETWORK_ID);
        // Server completion url empty and custom event has no server reward set

        ShadowLooper.pauseMainLooper();

        MoPubRewardedVideoManager.onRewardedVideoCompleted(TestCustomEvent.class, TestCustomEvent.AD_NETWORK_ID,
                moPubReward);

        ShadowLooper.unPauseMainLooper();

        ArgumentCaptor<Set<String>> rewardedIdsCaptor = ArgumentCaptor.forClass((Class) Set.class);
        verify(mockVideoListener).onRewardedVideoCompleted(rewardedIdsCaptor.capture(),
                eq(moPubReward));
        assertThat(rewardedIdsCaptor.getValue()).containsOnly("testAdUnit1", "testAdUnit2",
                "testAdUnit3");
    }

    public static class TestCustomEvent extends CustomEventRewardedVideo {
        public static final String AD_NETWORK_ID = "id!";

        boolean mPlayable = false;
        private Map<String, Object> mLocalExtras;

        @Nullable
        @Override
        protected CustomEventRewardedVideoListener getVideoListenerForSdk() {
            return null;
        }

        @Nullable
        @Override
        protected LifecycleListener getLifecycleListener() {
            return null;
        }

        @NonNull
        @Override
        protected String getAdNetworkId() {
            return AD_NETWORK_ID;
        }

        @Override
        protected void onInvalidate() {
            mPlayable = false;
        }

        @Override
        protected boolean checkAndInitializeSdk(@NonNull final Activity launcherActivity,
                @NonNull final Map<String, Object> localExtras,
                @NonNull final Map<String, String> serverExtras) throws Exception {
            return false;
        }

        @Override
        protected void loadWithSdkInitialized(@NonNull final Activity activity,
                @NonNull final Map<String, Object> localExtras,
                @NonNull final Map<String, String> serverExtras) throws Exception {
            // Do nothing because robolectric handlers execute immediately.
            mPlayable = true;
            MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(TestCustomEvent.class,
                    TestCustomEvent.AD_NETWORK_ID);
            mLocalExtras = localExtras;
        }

        @Override
        protected boolean hasVideoAvailable() {
            return mPlayable;
        }

        @Override
        protected void showVideo() {
            MoPubRewardedVideoManager.onRewardedVideoStarted(TestCustomEvent.class, TestCustomEvent.AD_NETWORK_ID);
        }

        @Nullable
        Map<String, Object> getLocalExtras() {
            return mLocalExtras;
        }
    }

    public static class NoVideoCustomEvent extends TestCustomEvent {
        @Override
        protected void loadWithSdkInitialized(@NonNull final Activity activity,
                @NonNull final Map<String, Object> localExtras,
                @NonNull final Map<String, String> serverExtras) throws Exception {
            mPlayable = false;
            MoPubRewardedVideoManager.onRewardedVideoLoadFailure(NoVideoCustomEvent.class, TestCustomEvent.AD_NETWORK_ID, MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    private static class RequestUrlContains extends ArgumentMatcher<Request> {

        private final String mMustContain;

        RequestUrlContains(String stringToFind) {
            mMustContain = stringToFind;
        }

        @Override
        public boolean matches(final Object argument) {
            return argument instanceof Request
                    && ((Request) argument).getUrl().contains(mMustContain);
        }
    }
}
