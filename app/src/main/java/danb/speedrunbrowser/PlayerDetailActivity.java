package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class PlayerDetailActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = PlayerDetailActivity.class.getSimpleName();

    public static final String ARG_PLAYER = "player";
    public static final String ARG_PLAYER_ID = "player_id";

    private CompositeDisposable mDisposables = new CompositeDisposable();

    private AppDatabase mDB;

    private User mPlayer;
    private AppDatabase.Subscription mSubscription;

    private Menu mMenu;

    private ProgressSpinnerView mSpinner;
    private View mPlayerHead;
    private View mScrollBests;
    private View mFrameBests;

    private ImageView mPlayerIcon;
    private TextView mPlayerName;

    private ImageView mIconTwitch;
    private ImageView mIconTwitter;
    private ImageView mIconYoutube;
    private ImageView mIconZSR;

    private LinearLayout mBestsFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_detail);

        setTitle(R.string.title_loading);

        mDB = AppDatabase.make(this);

        mSpinner = findViewById(R.id.spinner);
        mPlayerHead = findViewById(R.id.layoutPlayerHeader);
        mScrollBests = findViewById(R.id.scrollPlayerBests);
        mFrameBests = findViewById(R.id.framePlayerBests);

        mPlayerIcon = findViewById(R.id.imgAvatar);
        mPlayerName = findViewById(R.id.txtPlayerName);
        mIconTwitch = findViewById(R.id.iconTwitch);
        mIconTwitter = findViewById(R.id.iconTwitter);
        mIconYoutube = findViewById(R.id.iconYoutube);
        mIconZSR = findViewById(R.id.iconZSR);
        mBestsFrame = findViewById(R.id.bestsLayout);

        mIconTwitch.setOnClickListener(this);
        mIconTwitter.setOnClickListener(this);
        mIconYoutube.setOnClickListener(this);
        mIconZSR.setOnClickListener(this);

        Bundle args = getIntent().getExtras();

        if(args != null && (mPlayer = (User)args.getSerializable(ARG_PLAYER)) != null) {
            loadSubscription(mPlayer.id);
            setViewData();
        }
        else if(args != null && args.getString(ARG_PLAYER_ID) != null) {
            String pid = args.getString(ARG_PLAYER_ID);
            loadSubscription(pid);
            loadPlayer(pid);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDisposables.dispose();
        mDB.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.player, menu);
        mMenu = menu;
        setMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_subscribe) {
            toggleSubscribed();
            return true;
        }

        return false;
    }

    private void loadSubscription(final String playerId) {
        mDisposables.add(mDB.subscriptionDao().get(playerId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<AppDatabase.Subscription>() {
                @Override
                public void accept(AppDatabase.Subscription subscription) throws Exception {
                    mSubscription = subscription;
                    setMenu();
                }
            }));
    }

    private void loadPlayer(final String playerId) {
        Log.d(TAG, "Download playerId: " + playerId);

        /// TODO: ideally this would be zipped/run in parallel
        mDisposables.add(SpeedrunMiddlewareAPI.make().listPlayers(playerId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<User>>() {
                @Override
                public void accept(SpeedrunMiddlewareAPI.APIResponse<User> gameAPIResponse) throws Exception {

                    if (gameAPIResponse.data == null || gameAPIResponse.data.isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(PlayerDetailActivity.this, getString(R.string.error_missing_game, playerId));
                        return;
                    }

                    mPlayer = gameAPIResponse.data.get(0);

                    setViewData();
                }
            }, new ConnectionErrorConsumer(PlayerDetailActivity.this)));
    }

    private boolean toggleSubscribed() {

        MenuItem subscribeMenuItem = mMenu.findItem(R.id.menu_subscribe);

        ProgressSpinnerView psv = new ProgressSpinnerView(this, null);
        psv.setDirection(ProgressSpinnerView.Direction.RIGHT);
        psv.setScale(0.5f);

        subscribeMenuItem.setActionView(psv);




        final Consumer<Throwable> errorAction = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.w(TAG, "Could not add or remove subscription record from DB:", throwable);
            }
        };

        if(mSubscription != null) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(mSubscription.getFCMTopic())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {

                        Log.d(TAG, "Unsubscribe: " + mSubscription.getFCMTopic());
                        mDisposables.add(mDB.subscriptionDao().unsubscribe(mSubscription)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnError(errorAction)
                                .subscribe(new Action() {
                                    @Override
                                    public void run() throws Exception {
                                        mSubscription = null;
                                        setMenu();
                                        Util.showMsgToast(PlayerDetailActivity.this, getString(R.string.success_subscription));
                                    }
                                }));
                    }
                });
        }
        else if(mPlayer != null) {
            mSubscription = new AppDatabase.Subscription("player", mPlayer.id);

            FirebaseMessaging.getInstance().subscribeToTopic(mSubscription.getFCMTopic())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d(TAG, "Subscribe: " + mSubscription.getFCMTopic());

                        mDisposables.add(mDB.subscriptionDao().subscribe(mSubscription)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnError(errorAction)
                                .subscribe(new Action() {
                                    @Override
                                    public void run() throws Exception {
                                        setMenu();
                                        Util.showMsgToast(PlayerDetailActivity.this, getString(R.string.success_subscription));
                                    }
                                }));
                    }
                });

            return true;
        }

        return false;
    }

    private void setMenu() {
        if(mMenu == null)
            return;

        MenuItem subscribeItem = mMenu.findItem(R.id.menu_subscribe);
        subscribeItem.setActionView(null);
        if(mSubscription != null) {
            subscribeItem.setIcon(R.drawable.baseline_star_24);
            subscribeItem.setTitle(R.string.menu_unsubscribe);
        }
        else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24);
            subscribeItem.setTitle(R.string.menu_subscribe);
        }
    }

    private void setViewData() {

        setTitle(mPlayer.getName());

        // find out if we are subscribed
        setMenu();

        mPlayer.applyTextView(mPlayerName);

        mIconTwitch.setVisibility(mPlayer.twitch != null ? View.VISIBLE : View.GONE);
        mIconTwitter.setVisibility(mPlayer.twitter != null ? View.VISIBLE : View.GONE);
        mIconYoutube.setVisibility(mPlayer.youtube != null ? View.VISIBLE : View.GONE);
        mIconZSR.setVisibility(mPlayer.speedrunslive != null ? View.VISIBLE : View.GONE);


        if(!mPlayer.isGuest()) {
            try {
                mBestsFrame.setVisibility(View.VISIBLE);
                new DownloadImageTask(this, mPlayerIcon).clear(true).execute(new URL(String.format(Constants.AVATAR_IMG_LOCATION, mPlayer.names.get("international"))));
            } catch (MalformedURLException e) {
                Log.w(TAG, "Chould not show player logo:", e);
                mBestsFrame.setVisibility(View.GONE);
            }
        }
        else
            mBestsFrame.setVisibility(View.GONE);

        populateBestsFrame();

        mSpinner.setVisibility(View.GONE);
        mPlayerHead.setVisibility(View.VISIBLE);

        if(mScrollBests != null)
            mScrollBests.setVisibility(View.VISIBLE);
        if(mFrameBests != null)
            mFrameBests.setVisibility(View.VISIBLE);

    }

    @SuppressLint("SetTextI18n")
    private void populateBestsFrame() {
        if(mPlayer.bests == null)
            return;

        List<User.UserGameBests> playerGameBests = new ArrayList<>(mPlayer.bests.values());

        Collections.sort(playerGameBests, new Comparator<User.UserGameBests>() {
            @Override
            public int compare(User.UserGameBests o1, User.UserGameBests o2) {
                // find the min time
                LeaderboardRunEntry r1 = o1.getNewestRun();
                LeaderboardRunEntry r2 = o2.getNewestRun();

                if(r1 != null && r1.run.date != null && r2 != null && r2.run.date != null)
                    return -r1.run.date.compareTo(r2.run.date);
                else
                    return 0;
            }
        });

        for(User.UserGameBests gameBests : playerGameBests) {
            View gameLayout = getLayoutInflater().inflate(R.layout.content_game_personal_bests, null);

            ((TextView)gameLayout.findViewById(R.id.txtGameName)).setText(gameBests.names.get("international"));

            if(gameBests.assets.coverLarge != null) {
                ImageView imgView = gameLayout.findViewById(R.id.imgGameCover);
                new DownloadImageTask(this, imgView).clear(true).execute(gameBests.assets.coverLarge.uri);
            }

            List<PersonalBestRunRow> runsToAdd = new ArrayList<>();

            for(User.UserCategoryBest categoryBest : gameBests.categories.values()) {

                if(categoryBest.levels != null && !categoryBest.levels.isEmpty()) {
                    for(User.UserLevelBest levelBest : categoryBest.levels.values()) {
                        PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.name, levelBest.name, levelBest.run);
                        runsToAdd.add(rr);
                    }
                }
                else {
                    PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.name, null, categoryBest.run);
                    runsToAdd.add(rr);
                }
            }

            // sort these runs by date, descending
            Collections.sort(runsToAdd, new Comparator<PersonalBestRunRow>() {
                @Override
                public int compare(PersonalBestRunRow o1, PersonalBestRunRow o2) {
                    if(o1.re.run.date == null || o2.re.run.date == null)
                        return 0;
                    return -o1.re.run.date.compareTo(o2.re.run.date);
                }
            });

            TableLayout bestTable = gameLayout.findViewById(R.id.tablePersonalBests);

            for(final PersonalBestRunRow row : runsToAdd) {
                TableRow rowPersonalBest = (TableRow)getLayoutInflater().inflate(R.layout.content_row_personal_best, null);
                ((TextView)rowPersonalBest.findViewById(R.id.txtRunCategory)).setText(row.label);

                View placeImg = rowPersonalBest.findViewById(R.id.imgPlace);

                if(row.re.place == 1 && gameBests.assets.trophy1st != null) {
                    new DownloadImageTask(this, placeImg).execute(gameBests.assets.trophy1st.uri);
                }
                if(row.re.place == 2 && gameBests.assets.trophy2nd != null) {
                    new DownloadImageTask(this, placeImg).execute(gameBests.assets.trophy2nd.uri);
                }
                if(row.re.place == 3 && gameBests.assets.trophy3rd != null) {
                    new DownloadImageTask(this, placeImg).execute(gameBests.assets.trophy3rd.uri);
                }
                if(row.re.place == 4 && gameBests.assets.trophy4th != null) {
                    new DownloadImageTask(this, placeImg).execute(gameBests.assets.trophy4th.uri);
                }
                else
                    ((ImageView)placeImg).setImageDrawable(new ColorDrawable(Color.TRANSPARENT));

                ((TextView)rowPersonalBest.findViewById(R.id.txtPlace)).setText(row.re.getPlaceName());

                ((TextView)rowPersonalBest.findViewById(R.id.txtRunTime)).setText(row.re.run.times.formatTime());
                ((TextView)rowPersonalBest.findViewById(R.id.txtRunDate)).setText(row.re.run.date);

                rowPersonalBest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewRun(row.re.run.id);
                    }
                });

                bestTable.addView(rowPersonalBest);
            }

            mBestsFrame.addView(gameLayout);
        }
    }

    @Override
    public void onClick(View v) {

        URL selectedLink = null;

        if(v == mIconTwitch)
            selectedLink = mPlayer.twitch.uri;
        if(v == mIconTwitter)
            selectedLink = mPlayer.twitter.uri;
        if(v == mIconYoutube)
            selectedLink = mPlayer.youtube.uri;
        if(v == mIconZSR)
            selectedLink = mPlayer.speedrunslive.uri;

        if(selectedLink != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(selectedLink.toString()));
            startActivity(intent);
        }
    }

    private void viewRun(String runId) {
        Intent intent = new Intent(this, RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, runId);
        startActivity(intent);
    }

    private static class PersonalBestRunRow {

        public String label;
        public LeaderboardRunEntry re;

        public PersonalBestRunRow(String categoryName, String levelName, LeaderboardRunEntry re) {

            if(levelName != null)
                label = categoryName + " - " + levelName;
            else
                label = categoryName;

            this.re = re;
        }
    }
}
