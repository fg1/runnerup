/*
 * Copyright (C) 2014 weides@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.view;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.widget.LinearLayout;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;
import org.runnerup.widget.MyDotsPageIndicator;

@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class MainActivity extends Activity
        implements Constants, ValueModel.ChangeListener<TrackerState> {

    private GridViewPager pager;
    private StateService mStateService;
    final private ValueModel<TrackerState> trackerState = new ValueModel<TrackerState>();

    private static final int RUN_INFO_ROW = 0;
    private static final int PAUSE_RESUME_ROW = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pager = (GridViewPager) findViewById(R.id.pager);
        FragmentGridPagerAdapter pageAdapter = new PagerAdapter(getFragmentManager());
        pager.setAdapter(pageAdapter);

        LinearLayout verticalDotsPageIndicator = (LinearLayout) findViewById(R.id.vert_page_indicator);
        MyDotsPageIndicator dot2 = new MyDotsPageIndicator(verticalDotsPageIndicator);

        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        dotsPageIndicator.setDotFadeWhenIdle(false);
        dotsPageIndicator.setDotFadeOutDelay(1000 * 3600 * 24);
        dotsPageIndicator.setOnPageChangeListener(dot2);
        dotsPageIndicator.setOnAdapterChangeListener(dot2);
        dot2.setPager(pager);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getApplicationContext().bindService(new Intent(this, StateService.class),
                mStateServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onResume();
        if (mStateService != null) {
            mStateService.unregisterTrackerStateListener(this);
        }
        getApplicationContext().unbindService(mStateServiceConnection);
        mStateService = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private class PagerAdapter extends  FragmentGridPagerAdapter implements ValueModel.ChangeListener<TrackerState> {
        int rows = 1;
        int cols = 1;

        PauseResumeFragment pauseResumeFragment;

        public PagerAdapter(FragmentManager fm) {
            super(fm);
            update(trackerState.get());
            trackerState.registerChangeListener(this);
        }

        @Override
        public Fragment getFragment(int row, int col) {
            if (trackerState.get() == null)
                return new ConnectToPhoneFragment();

            switch (trackerState.get()) {
                case INIT:
                case INITIALIZING:
                case CLEANUP:
                case ERROR:
                    return new ConnectToPhoneFragment();
                case INITIALIZED:
                    return new StartFragment();
                case CONNECTING:
                    return new SearchingFragment();
                case CONNECTED:
                    return new StartFragment();
                case STARTED:
                case PAUSED:
                case STOPPED:
                    if (row == RUN_INFO_ROW) {
                        return new RunInfoFragment();
                    } else if (row == PAUSE_RESUME_ROW) {
                        if (trackerState.get() == TrackerState.STOPPED)
                            return new StoppedFragment();
                        else {
                            if (pauseResumeFragment == null)
                                pauseResumeFragment = new PauseResumeFragment();
                            return pauseResumeFragment;
                        }
                    }
            }
            return new ConnectToPhoneFragment();
        }

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount(int i) {
            return cols;
        }

        @Override
        public void onValueChanged(TrackerState oldValue, TrackerState newValue) {
            update(newValue);
            notifyDataSetChanged();
        }

        private void update(TrackerState newValue) {
            if (newValue == null) {
                cols = rows = 1;
                return;
            }
            switch (newValue) {
                case INIT:
                case INITIALIZING:
                case CLEANUP:
                case ERROR:
                case INITIALIZED:
                case CONNECTING:
                case CONNECTED:
                    cols = rows = 1;
                    break;
                case STARTED:
                case PAUSED:
                case STOPPED:
                    cols = 1;
                    rows = 2;
                    break;
            }
        }
    }

    Bundle getData(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getData(lastUpdateTime);
    }

    Bundle getHeaders(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getHeaders(lastUpdateTime);
    }

    public StateService getStateService() {
        return mStateService;
    }

    public void scrollToRunInfo() {
        Point curr = pager.getCurrentItem();
        pager.setCurrentItem(RUN_INFO_ROW, curr.x, true);
    }

    private ServiceConnection mStateServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mStateService == null) {
                mStateService = ((StateService.LocalBinder) service).getService();
                mStateService.registerTrackerStateListener(MainActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mStateService = null;
        }
    };

    public TrackerState getTrackerState() {
        if (mStateService == null)
            return null;
        synchronized (trackerState) {
            return mStateService.getTrackerState();
        }
    }

    @Override
    public void onValueChanged(final TrackerState oldState, final TrackerState newState) {
        synchronized (trackerState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (trackerState) {
                        trackerState.set(newState);
                    }
                }
            });
        }
    }

    public void registerTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
        synchronized (trackerState) {
            trackerState.registerChangeListener(listener);
        }
    }

    public void unregisterTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
        synchronized (trackerState) {
            trackerState.unregisterChangeListener(listener);
        }
    }
}
