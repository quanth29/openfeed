package com.williammora.openfeed.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.williammora.openfeed.R;
import com.williammora.openfeed.adapters.FeedAdapter;
import com.williammora.openfeed.dto.UserFeed;
import com.williammora.openfeed.listeners.OnRecyclerViewItemClickListener;
import com.williammora.openfeed.services.TwitterService;

import java.util.ArrayList;
import java.util.List;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class HomeFeedFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RecyclerView.OnScrollListener, OnRecyclerViewItemClickListener<Status> {

    private static String TAG = HomeFeedFragment.class.getSimpleName();

    private static final String SAVED_USER_FEED = "SAVED_USER_FEED";

    public interface HomeFeedFragmentListener {
        public void onRefreshRequested();

        public void onRefreshCompleted();
    }

    private RecyclerView mFeed;
    private FeedAdapter mAdapter;
    private SwipeRefreshLayout mFeedContainer;
    private HomeFeedFragmentListener mListener;
    private Twitter mTwitter;
    private UserFeed mUserFeed;
    private Paging mPaging;
    private boolean mRequestingMore;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        if (savedInstanceState != null) {
            mUserFeed = (UserFeed) savedInstanceState.getSerializable(SAVED_USER_FEED);
        } else {
            mUserFeed = new UserFeed();
            mUserFeed.setStatuses(new ArrayList<Status>());
        }

        mAdapter = new FeedAdapter(mUserFeed.getStatuses());
        mAdapter.setOnItemClickListener(this);

        mFeed = (RecyclerView) rootView.findViewById(R.id.feed);
        mFeed.setLayoutManager(new LinearLayoutManager(getActivity()));
        mFeed.setAdapter(mAdapter);
        mFeed.setOnScrollListener(this);

        mFeedContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.feed_container);
        mFeedContainer.setColorSchemeColors(getResources().getColor(R.color.openfeed_deep_orange_a400),
                getResources().getColor(R.color.openfeed_deep_orange_a700),
                getResources().getColor(R.color.openfeed_deep_orange_a200),
                getResources().getColor(R.color.openfeed_deep_orange_a100));
        mFeedContainer.setOnRefreshListener(this);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        if (activity instanceof HomeFeedFragmentListener) {
            mListener = (HomeFeedFragmentListener) activity;
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.setOAuthConsumerKey(getString(R.string.twitter_oauth_key));
            builder.setOAuthConsumerSecret(getString(R.string.twitter_oauth_secret));
            Configuration configuration = builder.build();
            TwitterFactory factory = new TwitterFactory(configuration);
            mTwitter = factory.getInstance();
            mTwitter.setOAuthAccessToken(TwitterService.getInstance().getAccessToken(activity));
        }
        super.onAttach(activity);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mUserFeed.getStatuses().isEmpty()) {
            mFeedContainer.setRefreshing(true);
            onRefresh();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(SAVED_USER_FEED, mUserFeed);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRefresh() {
        mPaging = new Paging();
        mPaging.count(50);
        if (!mUserFeed.getStatuses().isEmpty()) {
            mPaging.setSinceId(mUserFeed.getStatuses().get(0).getId());
        }
        requestMore(mPaging);
    }

    private void requestMore(Paging paging) {
        if (mRequestingMore) {
            return;
        }
        mListener.onRefreshRequested();
        new HomeFeedTask().execute(paging);
        mRequestingMore = true;
        mFeedContainer.setEnabled(false);
        mFeedContainer.setRefreshing(true);
    }

    public void onRequestCompleted() {
        mRequestingMore = false;
        mFeedContainer.setRefreshing(false);
        mListener.onRefreshCompleted();
        mFeedContainer.setEnabled(true);
    }

    public void updateStatuses(List<Status> statuses) {

        if (statuses == null || statuses.isEmpty()) {
            return;
        }

        // Timeline was empty
        if (mUserFeed.getStatuses().isEmpty()) {
            statuses.addAll(mUserFeed.getStatuses());
            mUserFeed.setStatuses(statuses);
            mAdapter.setDataset(mUserFeed.getStatuses());
        } else if (mUserFeed.getStatuses().get(mUserFeed.getStatuses().size() - 1).getId() == statuses.get(0).getId()) {
            // Previous statuses were requested
            statuses.remove(0);
            mUserFeed.getStatuses().addAll(statuses);
            mAdapter.addAll(statuses);
        } else {
            // Latest statuses
            mUserFeed.getStatuses().addAll(0, statuses);
            mAdapter.addAll(0, statuses);
            mFeed.smoothScrollToPosition(0);
        }

    }

    public void onScrollStateChanged(int i) {
    }

    public void onScrolled(int x, int y) {
        if (feedBottomReached()) {
            requestPreviousStatuses();
        }
    }

    private void requestPreviousStatuses() {
        mPaging = new Paging();
        mPaging.setMaxId(mUserFeed.getStatuses().get(mUserFeed.getStatuses().size() - 1).getId());
        mPaging.count(100);
        requestMore(mPaging);
    }

    private boolean feedBottomReached() {
        // Since we assigned the Status as the View tag we need to compare whatever is last
        // on screen vs the last element on the status list
        Status currentBottomStatus = (Status) mFeed.getChildAt(mFeed.getChildCount() - 1).getTag();
        Status lastStatus = mUserFeed.getStatuses().get(mUserFeed.getStatuses().size() - 1);
        if (lastStatus.isRetweet()) {
            lastStatus = lastStatus.getRetweetedStatus();
        }
        return currentBottomStatus == lastStatus;
    }

    @Override
    public void onItemClick(View view, Status status) {
        // TODO: Open detail activity
        Log.d(TAG, "Open detail for status " + status.getId());
    }

    private class HomeFeedTask extends AsyncTask<Paging, Void, List<Status>> {

        @Override
        protected List<twitter4j.Status> doInBackground(Paging... pagings) {
            try {
                return mTwitter.getHomeTimeline(mPaging);
            } catch (TwitterException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            onRequestCompleted();
        }

        @Override
        protected void onPostExecute(List<twitter4j.Status> statuses) {
            super.onPostExecute(statuses);
            if (statuses != null) {
                updateStatuses(statuses);
            }
            onRequestCompleted();
        }
    }
}
