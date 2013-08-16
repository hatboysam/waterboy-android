package com.google.plus.waterboy;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.plus.PlusClient;
import com.google.android.gms.plus.PlusClient.OnPersonLoadedListener;
import com.google.android.gms.plus.model.people.Person;
import com.google.plus.waterboy.PlusClientFragment.OnSignInListener;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends FragmentActivity 
    implements OnSignInListener, OnPersonLoadedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private PlusClientFragment mPlusClientFragment;
    private static final String[] SCOPES = { Scopes.PLUS_LOGIN };
    private static final String[] ACTIVITIES = {};

    private static final int REQUEST_CODE_AUTH = 9000;

    private LinearLayout mProfileInfoLayout;
    private SignInButton mSignInButton;
    private TextView mInfoTextView;
    private int mSignOutId;
    
    private Team mTeam;
    private Position mPosition;
    private Person mPerson;
    
    public enum Team { RED, BLUE, NONE }
    public enum Position { NONE, ONE, TWO }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlusClientFragment = PlusClientFragment.getPlusClientFragment(
                this, SCOPES, ACTIVITIES);

        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPlusClientFragment.signIn(REQUEST_CODE_AUTH);
            }
        });

        mProfileInfoLayout = (LinearLayout) findViewById(R.id.profile_info_layout);
        mInfoTextView = (TextView) findViewById(R.id.info_text_view);
        mInfoTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTeamDialog();
            }
        });
        
        mTeam = Team.NONE;
        mPosition = Position.NONE;
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent.getData() != null) {
            Log.i(TAG, intent.getData().toString());
        } else {
            Log.i(TAG, "NO INTENT DATA");
        }

        resetTeam();

        Bundle extras = intent.getExtras();
        if (extras != null) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                NdefMessage msg = (NdefMessage) rawMsgs[0];
                NdefRecord[] records = msg.getRecords();
                // Should be of the form blue.p1 or red.p2, etc.
                String payload = new String(records[0].getPayload());
                setTeamFromString(payload);
            }
        }
    }
    
    private void showTeamDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.team_dialog);
        dialog.setTitle("Choose Team");
        
        Spinner teamSpinner = (Spinner) dialog.findViewById(R.id.team_spinner);
        String[] teams = {"Blue, P1", "Blue, P2", "Red, P1", "Red, P2"};
        ArrayAdapter<String> teamsAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, teams);
        teamsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        teamSpinner.setAdapter(teamsAdapter);
        
        teamSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                setTeamFromString(selected.toLowerCase());
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {}
        });
        
        Button okButton = (Button) dialog.findViewById(R.id.team_dialog_ok);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkInPerson(mPerson);
                dialog.dismiss();
            }   
        });
        
        dialog.show();
    }

    /**
     * Reset to no team and no position.
     */
    private void resetTeam() {
        mTeam = Team.NONE;
        mPosition = Position.NONE;
        setViews();
    }

    /**
     * Decipher a string to determine the team and position.
     * 
     * @param payload
     */
    private void setTeamFromString(String payload) {
        if (payload == null) {
            payload = "unknown";
        }

        // Set team color
        if (payload.contains("blue")) {
            mTeam = Team.BLUE;
        } else if (payload.contains("red")) {
            mTeam = Team.RED;
        } else {
            mTeam = Team.NONE;
        }

        // Set player number
        if (payload.contains("1")) {
            mPosition = Position.ONE;
        } else if (payload.contains("2")) {
            mPosition = Position.TWO;
        } else {
            mPosition = Position.NONE;
        }
        
        setViews();
    }
    
    /**
     * Change the views based on the team and position.
     */
    private void setViews() {
        // Set team color
        switch (mTeam) {
            case BLUE:
                mInfoTextView.setBackgroundColor(getResources().getColor(R.color.blue));
                break;
            case RED:
                mInfoTextView.setBackgroundColor(getResources().getColor(R.color.red));
                break;
            default:
                mInfoTextView.setBackgroundColor(getResources().getColor(R.color.dark_grey));
        }

        // Set player number
        switch (mPosition) {
            case ONE:
                mInfoTextView.setText("P1");
                break;
            case TWO:
                mInfoTextView.setText("P2");
                break;
            default:
                mInfoTextView.setText(getResources().getString(R.string.unknown));
        }
        
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_AUTH) {
            mPlusClientFragment.handleOnActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu.size() == 0) {
            mSignOutId = menu.add("Sign Out").getItemId();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == mSignOutId) {
            mPlusClientFragment.signOut();
            mTeam = Team.NONE;
            mPosition = Position.NONE;
            setViews();
            return true;
        }
        return false;
    }


    @Override
    public void onSignedIn(PlusClient plusClient) {
        Log.i(TAG, "LOGGED IN");
        mProfileInfoLayout.setVisibility(View.VISIBLE);
        mSignInButton.setVisibility(View.GONE);

        mPlusClientFragment.getClient().loadPerson(this, "me");
    }


    @Override
    public void onSignInFailed() {
        Log.i(TAG, "LOGGED OUT");
        mProfileInfoLayout.setVisibility(View.GONE);
        mSignInButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPersonLoaded(ConnectionResult status, Person person) {
        // Check in with the server
        mPerson = person;
        checkInPerson(person);
        
        // Set the name
        TextView nameView = (TextView) findViewById(R.id.profile_name_view);
        nameView.setText(person.getDisplayName());
        // Download the image from the URL
        final String profPicUrl = person.getImage().getUrl();
        AsyncTask<Void,Void,Bitmap> task = new AsyncTask<Void,Void,Bitmap>() {

            @Override
            protected Bitmap doInBackground(Void... params) {
                InputStream is;
                try {
                    is = new BufferedInputStream(new URL(profPicUrl).openStream());
                    Bitmap picture = BitmapFactory.decodeStream(is);
                    return picture;
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Bad URL", e);
                    return null;
                } catch (IOException e) {
                    Log.e(TAG, "IO Problem", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                if (result != null) {
                    ImageView imageView = (ImageView) findViewById(R.id.profile_picture_view);
                    imageView.setImageBitmap(result);
                }
            }

        };
        task.execute();
    }
    
    /**
     * Send a player to the server.
     * 
     * @param person the {@link Person} to send.
     */
    private void checkInPerson(Person person) {
        if (mTeam == Team.NONE || mPosition == Position.NONE || person == null) {
            return;
        }
        
        final String id = person.getId();
        final String name = person.getDisplayName();
        final String imageUrl = person.getImage().getUrl();
        
        String host = getResources().getString(R.string.host);
        final String endpoint = host + "/checkin";
        
        final String params = "?"
                + "uid=" + URLEncoder.encode(id) + "&"
                + "name=" + URLEncoder.encode(name) + "&"
                + "image_url=" + URLEncoder.encode(imageUrl) + "&"
                + "team=" + URLEncoder.encode(mTeam.toString()) + "&"
                + "position=" + URLEncoder.encode(Integer.toString(mPosition.ordinal()));
        
        final String path = endpoint + params;
        
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {                
                try {
                    URL finalUrl = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) finalUrl.openConnection();
                    InputStream is = conn.getInputStream();
                    conn.disconnect();
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Malformed URL", e);
                } catch (IOException e) {
                    Log.e(TAG, "Couldn't connect", e);
                }
                
                return null;
            }
            
        };
        
        task.execute();
    }

}
