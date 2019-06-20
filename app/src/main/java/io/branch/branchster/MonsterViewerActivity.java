package io.branch.branchster;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

import io.branch.branchster.fragment.InfoFragment;
import io.branch.branchster.util.MonsterImageView;
import io.branch.branchster.util.MonsterObject;
import io.branch.branchster.util.MonsterPreferences;
import io.branch.indexing.BranchUniversalObject;
import io.branch.referral.Branch;
import io.branch.referral.BranchError;
import io.branch.referral.util.BRANCH_STANDARD_EVENT;
import io.branch.referral.util.BranchEvent;
import io.branch.referral.util.ContentMetadata;
import io.branch.referral.util.LinkProperties;

public class MonsterViewerActivity extends FragmentActivity implements InfoFragment.OnFragmentInteractionListener {
    static final int SEND_SMS = 12345;

    private static String TAG = MonsterViewerActivity.class.getSimpleName();
    public static final String MY_MONSTER_OBJ_KEY = "my_monster_obj_key";

    TextView monsterUrl;
    View progressBar;

    MonsterImageView monsterImageView_;
    MonsterObject myMonsterObject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monster_viewer);

        monsterImageView_ = (MonsterImageView) findViewById(R.id.monster_img_view);
        monsterUrl = (TextView) findViewById(R.id.shareUrl);
        progressBar = findViewById(R.id.progress_bar);

        // Change monster
        findViewById(R.id.cmdChange).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), MonsterCreatorActivity.class);
                startActivity(i);
                finish();
            }
        });

        // More info
        findViewById(R.id.infoButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                InfoFragment infoFragment = InfoFragment.newInstance();
                ft.replace(R.id.container, infoFragment).addToBackStack("info_container").commit();
            }
        });

        //Share monster
        findViewById(R.id.share_btn).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                shareMyMonster();
            }
        });

        initUI();
    }

    private void initUI() {
        myMonsterObject = getIntent().getParcelableExtra(MY_MONSTER_OBJ_KEY);

        //track branch event monster_view with state information from monsterMetaData()
        Map<String, String> monsterMetaData = myMonsterObject.monsterMetaData();

        BranchEvent monsterViewedEvent = new BranchEvent("monster_view");
        for(Map.Entry<String, String> entry : monsterMetaData.entrySet()){
            monsterViewedEvent.addCustomDataProperty(entry.getKey(), entry.getValue());
        }
        monsterViewedEvent.logEvent(getApplicationContext());

        if (myMonsterObject != null) {
            String monsterName = getString(R.string.monster_name);

            if (!TextUtils.isEmpty(myMonsterObject.getMonsterName())) {
                monsterName = myMonsterObject.getMonsterName();
            }

            ((TextView) findViewById(R.id.txtName)).setText(monsterName);
            String description = MonsterPreferences.getInstance(this).getMonsterDescription();

            if (!TextUtils.isEmpty(myMonsterObject.getMonsterDescription())) {
                description = myMonsterObject.getMonsterDescription();
            }

            ((TextView) findViewById(R.id.txtDescription)).setText(description);

            // set my monster image
            monsterImageView_.setMonster(myMonsterObject);

            //async method to get shortURL w/ params (keys from MonsterPreference, values from myMonsterObject.prepareBranchDict()
            new BranchShortUrl().execute("url");

            progressBar.setVisibility(View.GONE);
        } else {
            Log.e(TAG, "Monster is null. Unable to view monster");
        }
    }

    /**
     * Method to share my custom monster with sharing with Branch Share sheet
     */
    private void shareMyMonster() {
        progressBar.setVisibility(View.VISIBLE);

        //async method to generate shortUrl and starting the SMS intent
        new BranchShortUrl().execute("share");

        progressBar.setVisibility(View.GONE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (SEND_SMS == requestCode) {
            if (RESULT_OK == resultCode) {
                //track share event via Branch
                new BranchEvent(BRANCH_STANDARD_EVENT.SHARE).logEvent(getApplicationContext());

                Log.i("SMS", "Branch event logged");
            }
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Exit")
                    .setMessage("Are you sure you want to exit?")
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).create().show();
        }
    }


    @Override
    public void onFragmentInteraction() {
        //no-op
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initUI();
    }

    private class BranchShortUrl extends AsyncTask<String, Void, String>{
        private Map<String, String> monsterDictionary;
        private BranchUniversalObject buo;
        private LinkProperties lp;

        @Override
        protected String doInBackground(String... strings) {
            //capture monster data via prepareBranchDict()
            monsterDictionary = myMonsterObject.prepareBranchDict();

            //create payload (ContentMetadata) by mapping from monsterDictionary
            ContentMetadata monsterData = new ContentMetadata();
            for(Map.Entry<String, String> entry : monsterDictionary.entrySet()){
                monsterData.addCustomMetadata(entry.getKey(), entry.getValue());
            }

            //create content reference - branch universal object to hold monster data
            buo = new BranchUniversalObject()
                    .setTitle(myMonsterObject.getMonsterName())
                    .setContentDescription(myMonsterObject.getMonsterDescription())
                    .setContentImageUrl(myMonsterObject.getMonsterImage())
                    .setContentIndexingMode(BranchUniversalObject.CONTENT_INDEX_MODE.PUBLIC)
                    .setLocalIndexMode(BranchUniversalObject.CONTENT_INDEX_MODE.PUBLIC)
                    .setContentMetadata(monsterData);

            //create deep link
            lp = new LinkProperties()
                    .setChannel("sms")
                    .setFeature("sharing");

            return strings[0];
        }//end doInBackground(...)

        @Override
        protected void onPostExecute(final String result){
            buo.generateShortUrl(getApplicationContext(), lp, new Branch.BranchLinkCreateListener() {
                @Override
                public void onLinkCreate(String url, BranchError error) {
                    if (error == null) {
                        Log.i("BRANCH SDK", "got my Branch link to share: " + url);

                        //track new event: branch_url_created
                        new BranchEvent("branch_url_created")
                                .addCustomDataProperty("url", url)
                                .logEvent(getApplicationContext());

                        if(result.equals("share")){
                            //initialize intent, load monster name, and start activity
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(Intent.EXTRA_TEXT, String.format("Check out my Branchster named %s at %s", myMonsterObject.getMonsterName(), url));
                            startActivityForResult(i, SEND_SMS);
                        }
                        else if(result.equals("url")){
                            //put url into monsterUrl
                            monsterUrl.setText(url);
                        }


                    } else {
                        //track new event: branch_error
                        new BranchEvent("branch_error")
                                .addCustomDataProperty("url", url)
                                .logEvent(getApplicationContext());
                    }
                    progressBar.setVisibility(View.GONE);
                }
            });

        }
    }
}
