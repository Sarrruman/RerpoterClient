package rs.etf.m2m.reporter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;

import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {
    static final int REQUEST_TAKE_PHOTO = 1;
    static final int REQUEST_PLACE_PICKER = 2;

    private long mLastClickTime = 0;

    String mCurrentPhotoPath;
    private GoogleApiClient mGoogleApiClient;

    ImageView displayCaptured;
    Button captureButton;
    Button locationButton;
    TextView displayLocation;
    EditText comment;
    ProgressBar mLoadingIndicator;

    private static Bitmap bitmap = null;
    private static double locationLat = 0;
    private static double locationLon = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayCaptured = (ImageView) findViewById(R.id.display_captured);
        captureButton = (Button) findViewById(R.id.capture_button);
        locationButton = (Button) findViewById(R.id.location_button);
        displayLocation = (TextView) findViewById(R.id.display_location);
        comment = (EditText) findViewById(R.id.comment_field);
        mLoadingIndicator = (ProgressBar) findViewById(R.id.pb_loading_indicator);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakeLocationIntent();
            }
        });

        comment.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {

                if (v.getId() == R.id.comment_field && !hasFocus) {

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                }
            }
        });

        // initialize google api client
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();
    }

    private void dispatchTakeLocationIntent() {
        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        try {
            startActivityForResult(builder.build(this), REQUEST_PLACE_PICKER);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "rs.etf.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            setPic();
        } else if (requestCode == REQUEST_PLACE_PICKER) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(data, this);
                String locationResult = String.format("Place: %s", place.getName());
                displayLocation.setText(locationResult);
                locationLat = place.getLatLng().latitude;
                locationLon = place.getLatLng().longitude;
            }
        }
    }

    private void setPic() {
        // Get the dimensions of the View
        int targetW = displayCaptured.getWidth();
        int targetH = displayCaptured.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        displayCaptured.setImageBitmap(bitmap);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Use AppCompatActivity's method getMenuInflater to get a handle on the menu inflater */
        MenuInflater inflater = getMenuInflater();
        /* Use the inflater's inflate method to inflate our menu layout to this menu */
        inflater.inflate(R.menu.submit, menu);
        /* Return true so that the menu is displayed in the Toolbar */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // mis-clicking prevention, using threshold of 1000 ms
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return false;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(comment.getWindowToken(), 0);

        if (id == R.id.action_submit) {
            // logic to submit request and clear data...
            String errorMsg = checkFields();
            if (errorMsg != null) {
                // notificatrion
                Toast toast = Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG);
                toast.show();
            } else {
                mLoadingIndicator.setVisibility(View.VISIBLE);
                // submit
                String data = "{" + "'photo': '" + ImageUtil.encodeToBase64(bitmap, Bitmap.CompressFormat.JPEG, 100) + "',"
                        + "'longitude': " + locationLon + ", " + "'latitude': " + locationLat + ", "
                        + "'comment': '" + comment.getText().toString() + "'" +
                        "}";
                // wireless ipv4 address obtained by ipconfig
                String url = "http://91.187.151.128:8080/reporter/rest/report";
                // Log.i("----------> Json data: ", data);
                RequestQueue queue = Volley.newRequestQueue(this);
                JsonObjectRequest jsonObjectRequest = null;
                try {
                    jsonObjectRequest = new JsonObjectRequest
                            (Request.Method.POST, url, new JSONObject(data), new Response.Listener<JSONObject>() {

                                @Override
                                public void onResponse(JSONObject response) {
                                    mLoadingIndicator.setVisibility(View.INVISIBLE);
                                    if (response == null) {
                                        Toast toast = Toast.makeText(getApplicationContext(), "Connection problem", Toast.LENGTH_LONG);
                                        toast.show();
                                    } else {
                                        Log.i("---------> response: ", response.toString());
                                        Toast toast = Toast.makeText(getApplicationContext(), "Success", Toast.LENGTH_LONG);
                                        toast.show();
                                        refreshFields();
                                    }
                                }
                            }, new Response.ErrorListener() {

                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // TODO: Handle error
                                    mLoadingIndicator.setVisibility(View.INVISIBLE);
                                    error.printStackTrace();
                                    Log.i("---------> error: ", error.getLocalizedMessage() + "..." + error.getCause());
                                    Toast toast = Toast.makeText(getApplicationContext(), "Connection error", Toast.LENGTH_LONG);
                                    toast.show();
                                }
                            });
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                queue.add(jsonObjectRequest);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private String checkFields() {
        if (bitmap == null) return "No photo taken!";
        if (displayLocation.getText() == null || displayLocation.getText().toString().isEmpty())
            return "No location selected!";
        return null;
    }

    private void refreshFields() {
        bitmap = null;
        locationLat = 0;
        locationLon = 0;
        displayLocation.setText("Location: ");
        comment.setText(null);
    }

    public class Submitter extends AsyncTask<URL, Void, String> {

        @Override
        protected String doInBackground(URL... urls) {
            return "";
        }

        @Override
        protected void onPostExecute(String s) {

        }
    }

}
