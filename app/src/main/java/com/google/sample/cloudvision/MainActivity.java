/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cloudvision;

import android.Manifest;
import android.app.Activity;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.nightonke.boommenu.ButtonEnum;
import com.nightonke.boommenu.Piece.PiecePlaceEnum;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;


public class MainActivity extends AppCompatActivity {
    public static Activity _MainActivity;
    //list
    private List<SongItem> items = new ArrayList<>();
    ArrayList<SongItem> itemArrayList = new ArrayList<>();
    private RecommendListAdapter adapter =
            new RecommendListAdapter(MainActivity.this, items, R.layout.activity_main);
    private TextView final_w;
    private TextView u_genre;
    private Switch switch_g;

    private static final String CLOUD_VISION_API_KEY = BuildConfig.API_KEY;
    public static final String FILE_NAME = "temp.jpg";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;

    private String imagepath;
    private TextView mImageDetails;
    private Button survey;
    private ImageView mMainImage;
    private BoomMenuButton bmb;
    public RecyclerView recyclerView;
    private Integer color_num;
    private Integer obj_num;

    List<Object> ObjectArray = new ArrayList<Object>();
    List<Double> valence = new ArrayList<Double>();
    List<Double> arousal = new ArrayList<Double>();
    List<Double> valence_H = new ArrayList<Double>();
    List<Double> valence_L = new ArrayList<Double>();
    List<Double> arousal_H = new ArrayList<Double>();
    List<Double> arousal_L = new ArrayList<Double>();
    Double valence_final_obj;
    Double arousal_final_obj;
    Double color_weight;
    Double object_weight;
    Bitmap bitmap_;
    static String[] colorResults = {};

    private FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference myRef = database.getReference("Song");
    private DatabaseReference surveyRef=database.getReference("Survey");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().hide();
        _MainActivity = MainActivity.this;

        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        DatabaseReference databaseReference = firebaseDatabase.getReference("User");

        u_genre = (TextView) findViewById(R.id.tv_genre);

        recyclerView = findViewById(R.id.recommend_recyclerView);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);


        if (user == null) {
            u_genre.setText("");
        } else {
            Query query = databaseReference.orderByChild("email").equalTo(user.getEmail());
            query.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot ds : dataSnapshot.getChildren()) {
                        String genre = "" + ds.child("genre").getValue();
                        u_genre.setText(genre);
                        u_genre.setVisibility(View.INVISIBLE);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder
                    .setMessage("사진을 선택해주세요")
                    .setPositiveButton("갤러리", (dialog, which) -> startGalleryChooser())
                    .setNegativeButton("카메라", (dialog, which) -> startCamera());
            builder.setNeutralButton("취소", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    recyclerView.setVisibility(View.VISIBLE);
                    if (final_w != null) {
                        if (u_genre.getText().toString() != "") {
                            switch_g.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
            ObjectArray.clear();
            valence.clear();
            arousal.clear();
            valence_H.clear();
            valence_L.clear();
            arousal_H.clear();
            arousal_L.clear();
            survey.setVisibility(View.INVISIBLE);
            //items.clear();
            recyclerView.setVisibility(View.INVISIBLE);
            switch_g.setVisibility(View.INVISIBLE);
            builder.create().show();
        });


        //리스트 아이템 클릭하면
        adapter.setOnItemClickListener(new RecommendListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                Intent intent = new Intent(getApplicationContext(), MusicListActivity.class);
                intent.putExtra("title", items.get(position).getTitle());
                intent.putExtra("performer", items.get(position).getPerformer());
                intent.putExtra("genre", items.get(position).getGenre());
                intent.putExtra("adj",final_w.getText().toString());

                //이미지 인텐트(Intent) 이동
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                Bitmap sendBitmap = ((BitmapDrawable) mMainImage.getDrawable()).getBitmap();
                sendBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                byte[] byteArray = stream.toByteArray();
                intent.putExtra("image", byteArray);
                startActivity(intent);
            }
        });

        //프로필 & 좋아한곡, 로그아웃 액티비티 버튼
        bmb = findViewById(R.id.boom);
        bmb.setButtonEnum(ButtonEnum.Ham);
        bmb.setPiecePlaceEnum(PiecePlaceEnum.DOT_3_1);
        bmb.setButtonPlaceEnum(ButtonPlaceEnum.HAM_3);
        bmb.setDelay(0);
        if (user == null) {
            bmb.setVisibility(View.INVISIBLE);
        } else {
            bmb.setVisibility(View.VISIBLE);
        }

        for (int i = 0; i < bmb.getPiecePlaceEnum().pieceNumber(); i++) {
            int position = i;
            //유저 프로파일
            if (i == 0) {
                HamButton.Builder builder2 = new HamButton.Builder()
                        .normalColor(Color.WHITE)
                        .normalTextColor(Color.BLACK)
                        .normalText("User Profile").listener(new OnBMClickListener() {
                            @Override
                            public void onBoomButtonClick(int index) {
                                if (user == null) {
                                    Toast.makeText(MainActivity.this, "로그인 되어있지 않습니다.", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    //StartActivity _StartActivity = (StartActivity) StartActivity._StartActivity;
                                    //_StartActivity.finish();
                                    //finish();
                                } else {
                                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                    startActivity(intent);
                                }
                            }
                        });
                bmb.addBuilder(builder2);
            } else if (i == 1) {
                //like 곡
                HamButton.Builder builder2 = new HamButton.Builder().normalText("Favorite Song")
                        .normalColor(Color.WHITE)
                        .normalTextColor(Color.BLACK)
                        .listener(new OnBMClickListener() {
                            @Override
                            public void onBoomButtonClick(int index) {
                                if (user == null) {
                                    Toast.makeText(MainActivity.this, "로그인 되어있지 않습니다.", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    // StartActivity _StartActivity = (StartActivity) StartActivity._StartActivity;
                                    //_StartActivity.finish();
                                    //finish();
                                } else {
                                    Intent intent = new Intent(MainActivity.this, LikeListActivity.class);
                                    startActivity(intent);
                                }
                            }
                        });
                bmb.addBuilder(builder2);
            } else if (i == 2) {
                //로그아웃
                if (user == null) {
                    HamButton.Builder builder2 = new HamButton.Builder().normalText("Login")
                            .normalColor(Color.WHITE)
                            .normalTextColor(Color.BLACK)
                            .listener(new OnBMClickListener() {
                                @Override
                                public void onBoomButtonClick(int index) {
                                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    //finish();
                                }
                            });
                    bmb.addBuilder(builder2);
                } else {
                    HamButton.Builder builder2 = new HamButton.Builder().normalText("Logout")
                            .normalColor(Color.WHITE)
                            .normalTextColor(Color.BLACK)
                            .listener(new OnBMClickListener() {
                                @Override
                                public void onBoomButtonClick(int index) {
                                    firebaseAuth.signOut();
                                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    finish();
                                }
                            });
                    bmb.addBuilder(builder2);
                }
            }

        }
        mImageDetails = findViewById(R.id.image_details);
        survey = (Button)findViewById(R.id.survey);
        survey.setVisibility(View.INVISIBLE);
        mMainImage = findViewById(R.id.main_image);
        findViewById(R.id.loadingPanel).setVisibility(View.GONE);

        switch_g = findViewById(R.id.switch_genre);
        switch_g.setVisibility(View.GONE);
        //CheckState();
        switch_g.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckState();
            }
        });

        surveyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    color_num =dataSnapshot.child("color_count").getValue(Integer.class);
                    obj_num = dataSnapshot.child("object_count").getValue(Integer.class);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    //장르 스위치 상태체크
    private void CheckState() {
        String u_g = u_genre.getText().toString();

        if (u_g == "") {
            if (switch_g.isChecked()) {
                Toast.makeText(this, "장르 반영을 할 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (switch_g.isChecked()) {
                items.clear();

                StringTokenizer sT = new StringTokenizer(u_g, ", ");
                int token_num = sT.countTokens();
                String[] arr = new String[token_num];
                Log.d("toke", "" + sT.countTokens());
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = sT.nextToken();
                    Log.d("toke11", "" + arr[i]);
                }
                SQLite helper;
                SQLiteDatabase db;
                helper = new SQLite(this);
                Cursor cursor;

                String final_wo = final_w.getText().toString();

                String title;
                String performer;
                String genre;
                int count = 0;
                db = helper.getReadableDatabase();
                if (token_num == 1) {
                    cursor = db.rawQuery("SELECT title,performer,genre FROM music_to_value_final where (word='" + final_wo + "') AND (genre LIKE '%" + arr[0] + "%')  order by random();", null);
                } else if (token_num == 2) {
                    cursor = db.rawQuery("SELECT title,performer,genre FROM music_to_value_final where ((word='" + final_wo + "') AND ((genre LIKE '%" + arr[0] + "%') OR (genre LIKE '%" + arr[1] + "%'))) order by random();", null);
                } else {
                    cursor = db.rawQuery("SELECT title,performer,genre FROM music_to_value_final where ((word='" + final_wo + "') AND ((genre LIKE '%" + arr[0] + "%') OR (genre LIKE '%" + arr[1] + "%') OR (genre LIKE '%" + arr[2] + "%'))) order by random();", null);
                }

                if (!cursor.moveToNext()) {
                    performer = "장르 반영 검색 결과가 없습니다.";
                    title = "";
                    genre = "";
                    SongItem item = new SongItem(title, performer, genre);
                    items.add(item);
                }

                while (cursor.moveToNext() && count < 5) {
                    title = cursor.getString(0);
                    performer = cursor.getString(1);
                    genre = cursor.getString(2);
                    SongItem item = new SongItem(title, performer, genre);
                    items.add(item);
                    count++;

                    //recommend song 저장
                    myRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            String Title = item.getTitle();
                            String Performer = item.getPerformer();
                            myRef.child(Title).child("performer").setValue(Performer);

                            //노래 추천된 count
                            DatabaseReference upRef = myRef.child(Title).child("count");
                            upRef.runTransaction(new Transaction.Handler() {
                                @Override
                                public Transaction.Result doTransaction(MutableData currentData) {
                                    if (currentData.getValue() == null) {
                                        currentData.setValue(1);
                                    } else {
                                        currentData.setValue((Long) currentData.getValue() + 1);
                                    }
                                    return Transaction.success(currentData);
                                }
                                @Override
                                public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot currentData) {
                                }
                            });

                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                        }
                    });
                }
                adapter.notifyDataSetChanged();
            } else {
                items.clear();
                //String final_wo = final_w.getText().toString();
                //find_music(final_wo);
                for (int i=0; i<5; i++) {
                    items.add(itemArrayList.get(i));
                }
                adapter.notifyDataSetChanged();
            }
        }
    }

    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_PICK);
            startActivityForResult(Intent.createChooser(intent, "사진을 선택해주세요."),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        imagepath = dir.getAbsolutePath() + "/" + FILE_NAME;
        return new File(dir, FILE_NAME);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imagepath = getRealPathFromURI(data.getData());
            bitmap_ = uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            bitmap_ = uploadImage(photoUri);
        }

    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                    startCamera();
                }
                break;
            case GALLERY_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                    startGalleryChooser();
                }
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Bitmap uploadImage(Uri uri) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                bitmap = scaleBitmapDown(MediaStore.Images.Media.getBitmap(getContentResolver(), uri), MAX_DIMENSION);
                int degree = getExifOrientation(imagepath);
                bitmap = getRotatedBitmap(bitmap, degree);
                callCloudVision(bitmap);

                mMainImage.setImageBitmap(bitmap);
                mMainImage.setDrawingCacheEnabled(true);
                mMainImage.buildDrawingCache();

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
        return bitmap;
    }

    //사진 회전 문제
    private String getRealPathFromURI(Uri contentURI){
        String[] proj = { MediaStore.Images.Media.DATA };

        CursorLoader cursorLoader = new CursorLoader(this, contentURI, proj, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();

        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
    //회전 각도 구하기
    private int getExifOrientation(String filePath) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            if (orientation != -1) {
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        return 90;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        return 180;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        return 270;
                }
            }
        }

        return 0;
    }
    //이미지 회전하기
    private Bitmap getRotatedBitmap(Bitmap bitmap, int degree) {
        if (degree != 0 && bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.setRotate(degree, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);

            try {
                Bitmap tmpBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                if (bitmap != tmpBitmap) {
                    bitmap.recycle();
                    bitmap = tmpBitmap;
                }
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }
        }

        return bitmap;
    }

    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature labelDetection = new Feature();
                labelDetection.setType("LABEL_DETECTION");
                labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(labelDetection);
            }});

            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }

    private class LableDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        LableDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
        }

        @Override
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            ColorData a = new ColorData();
            int[] colorInts = a.getColorScale(bitmap_);
            colorResults = a.getSimilarScale(colorInts[0], colorInts[1], colorInts[2]);
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.image_details);
                List<Object> result_word = objectToAV();
                Iterator iterator = result_word.iterator();
                int count = 0;
                int i = 0;
                while (iterator.hasNext()) {
                    String word = (String) (iterator.next());
                    // valence,arousal 배열에 값 넣기
                    if ((count % 3) == 1) {
                        valence.add(Double.parseDouble(word));
                    }
                    if ((count % 3) == 2) {
                        arousal.add(Double.parseDouble(word));
                    }
                    count += 1;
                }
                valence_final_obj=range()[0];
                arousal_final_obj=range()[1];
                Double[] final_value=combine_Attribute(valence_final_obj,arousal_final_obj);
                String final_word=find_adj(final_value[0],final_value[1]);
                find_music(final_word);

                result="";

                /*result = result + word_obj+" "+valence_final_obj+" , "+arousal_final_obj+"\nColor:\n" //여기까지
                        + word + " " + colorResults[1] + " , " + colorResults[2]+"\n\n"
                        +final_value[0].toString()+","
                        +final_value[1].toString()+" "
                        +final_word;
*/
                imageDetail.setText(result);
                final_w = (TextView) findViewById(R.id.final_w);
                final_w.setText(final_word);
                final_w.setVisibility(View.INVISIBLE);
                if (u_genre.getText().toString()=="") {
                    switch_g.setVisibility(View.GONE);
                } else {
                    switch_g.setVisibility(View.VISIBLE);
                }
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            }
        }
    }

    private void callCloudVision(final Bitmap bitmap) {
        // Switch text to loading
        mImageDetails.setText("노래 추천을 위한 사진 분석 중입니다.");
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        // Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, String> labelDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap));
            labelDetectionTask.execute();
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    //DB에서 AV값 꺼내기
    public List<Object> objectToAV() {
        SQLite helper;
        SQLiteDatabase db;
        Cursor cursor;
        Iterator iterator = ObjectArray.iterator();
        List<Object> word = new ArrayList<Object>();

        helper = new SQLite(this);
        db = helper.getReadableDatabase();
        while (iterator.hasNext()) {
            cursor = db.rawQuery("SELECT * FROM valence_arousal where word='" + (String) ((String) iterator.next()).toLowerCase() + "';", null);
            while (cursor.moveToNext()) {
                word.add(cursor.getString(0)); //word
                word.add(cursor.getString(1)); //valence
                word.add(cursor.getString(2)); //arousal
            }
        }
        Log.d("word",word+"");
        return word;
    }

    //가까운 노래 형용사 찾기
    public String find_adj(double valence_obj,double arousal_obj){
        SQLite helper;
        SQLiteDatabase db;
        Cursor cursor;
        Double arousal_pow;
        Double valence_pow;
        Double sum_pow;
        Double temp=100.0;
        Double valence_real=0.0;
        Double arousal_real=0.0;
        String word="a";
        helper = new SQLite(this);
        db = helper.getReadableDatabase();
        cursor = db.rawQuery("SELECT * FROM criteria_adj;", null);
        while (cursor.moveToNext()) {
            valence_pow = Math.pow((cursor.getDouble(1) - valence_obj), 2);
            arousal_pow = Math.pow((cursor.getDouble(2) - arousal_obj), 2);
            sum_pow = valence_pow + arousal_pow;
            if (sum_pow < temp) {
                valence_real = cursor.getDouble(1);
                arousal_real = cursor.getDouble(2);
                temp=sum_pow;
                word=cursor.getString(0);
            }
        }
        cursor.close();
        return word;
    }

    //Object Detection 결과값 받아오기
    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder("Keyword: \n");
        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                ObjectArray.add(String.format(Locale.US, "%s", label.getDescription()));
            }
        } else {
            message.append("nothing");
        }

        return message.toString();
    }

    //valence arousal 값 범위 안에 속하는지 확인하기
    public Double[] range() {
        List<Integer> arousal_int=new ArrayList<Integer>();
        List<Integer> valence_int=new ArrayList<Integer>();
        Double temp;
        Iterator iterator = arousal.iterator();
        int arousal_count=0;
        while (iterator.hasNext()) {
            temp = (Double) iterator.next();
            if (temp <= 5) {
                arousal_int.add(arousal_count,0);
            } else if (temp > 5) {
                arousal_int.add(arousal_count,1);
            }
            arousal_count++;
        }

        Double temp1;
        Iterator iterator1 = valence.iterator();
        int valence_count=0;
        while (iterator1.hasNext()) {
            temp1 = (Double) iterator1.next();
            if (temp1 <= 5) {
               valence_int.add(valence_count,0);
            } else if (temp1 > 5) {
                valence_int.add(valence_count,1);
            }
            valence_count++;
        }
        Iterator arousal_iterator=arousal_int.iterator();
        int count=0;
        int num1=0,num2=0,num3=0,num4=0;
        int temp_int;
        while(arousal_iterator.hasNext()){
            temp_int = (int)arousal_iterator.next();
            if(valence_int.get(count)==1&&temp_int==1){
                num1++;
            }else if(valence_int.get(count)==1&&temp_int==0){
                num2++;
            }else if(valence_int.get(count)==0&&temp_int==0){
                num3++;
            }else if(valence_int.get(count)==0&&temp_int==1){
                num4++;
            }
            count++;
        }

        int[] num={num1,num2,num3,num4};
        int max=num[0];
        for(int i=0;i<num.length;i++) {
            if (max < num[i]) {
                max = num[i];
            }
        }

        count=0;
        Double sum1_a=0.0;Double sum2_a=0.0;Double sum3_a=0.0;Double sum4_a=0.0;
        Double sum1_v=0.0;Double sum2_v=0.0;Double sum3_v=0.0;Double sum4_v=0.0;
        Double avg_v=0.0; Double avg_a=0.0;
        arousal_iterator=arousal_int.iterator();
        if (max==num1) {
            while (arousal_iterator.hasNext()) {
                temp_int = (int)arousal_iterator.next();
                if (valence_int.get(count) == 1 && temp_int == 1) {
                    sum1_v += valence.get(count);
                    sum1_a+=arousal.get(count);
                }
                count++;
            }
            avg_v=sum1_v/num1;
            avg_a=sum1_a/num1;
        }
        else if(max==num2){
            while (arousal_iterator.hasNext()) {
                temp_int = (int)arousal_iterator.next();
                if (valence_int.get(count) == 1 && temp_int  == 0) {
                    sum2_v += valence.get(count);
                    sum2_a+=arousal.get(count);
                }
                count++;
            }
            Log.d("sum_v",""+sum2_v);
            Log.d("sum_a",""+sum2_a);
            avg_v=sum2_v/num2;
            avg_a=sum2_a/num2;
        }
        else if (max==num3){
            while (arousal_iterator.hasNext()) {
                temp_int = (int)arousal_iterator.next();
                if (valence_int.get(count) == 0 && temp_int == 0) {
                    sum3_v += valence.get(count);
                    sum3_a+=arousal.get(count);
                }
                count++;
            }
            avg_v=sum3_v/num3;
            avg_a=sum3_a/num3;
        }
        else if (max==num4){
            while (arousal_iterator.hasNext()) {
                temp_int = (int)arousal_iterator.next();
                if (valence_int.get(count) == 0 && temp_int == 1) {
                    sum4_v += valence.get(count);
                    sum4_a+=arousal.get(count);
                }
                count++;
            }
            avg_v=sum4_v/num4;
            avg_a=sum4_a/num4;
        }

        Double[] avg={avg_v,avg_a};
        return avg;
    }

    //valence arousal 색깔, 오브젝트 평균 내기
    public Double[] combine_Attribute(Double obj_v,Double obj_a){
        Double color_v=Double.parseDouble(colorResults[1]);
        Double color_a=Double.parseDouble(colorResults[2]);
        Log.d("colornum",""+color_num);
        Log.d("objnum",""+obj_num);
        color_weight=(double)(1366+color_num)/(2880+color_num+obj_num);
        object_weight=(double)(1514+obj_num)/(2880+color_num+obj_num);
        Double final_v=0.0;
        Double final_a=0.0;
       //같은 사분면일때
        //1사분면
        if(obj_v>5&&obj_a>5&& color_v>5&& color_a>5) {
            final_v=obj_v*object_weight+color_v*color_weight;
            final_a=obj_a*object_weight+color_a*color_weight;
        }
        //2사분면
        else if(obj_v>5&&obj_a<=5&& color_v>5&& color_a<=5) {
            final_v=obj_v*object_weight+color_v*color_weight;
            final_a=obj_a*object_weight+color_a*color_weight;
        }
        //3사분면
        else if(obj_v<=5&&obj_a<=5&& color_v<=5&& color_a<=5) {
            final_v=obj_v*object_weight+color_v*color_weight;
            final_a=obj_a*object_weight+color_a*color_weight;
        }
        //4사분면
        else if(obj_v<=5&&obj_a>5&& color_v<=5&& color_a>5) {
            final_v=obj_v*object_weight+color_v*color_weight;
            final_a=obj_a*object_weight+color_a*color_weight;
        }
        //다른 사분면일때
        else{

            if(color_weight>=object_weight){
                final_v=color_v;
                final_a=color_a;
            }
            else if(color_weight<=object_weight){
                final_v=obj_v;
                final_a=obj_a;
            }
        }
        survey.setVisibility(View.VISIBLE);
        String object_adj=find_adj(obj_v,obj_a);
        String color_adj =find_adj(color_v, color_a);
        String[] adj_ary = {object_adj, color_adj};
        survey.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder
                    .setTitle("더 어울리는 형용사를 골라주세요")
                    .setItems(adj_ary, (dialogInterface, i) -> {
                        if(i == 0) object_count();
                        if(i == 1) color_count();
                        survey.setVisibility(View.INVISIBLE);
                    });
            builder.create().show();
        });
        Double[] final_va={final_v,final_a};
        return final_va;
    }

    public void object_count() {
        surveyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                DatabaseReference objRef=surveyRef.child("object_count");
                objRef.runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData currentData) {
                        if (currentData.getValue() == null) {
                            currentData.setValue(1);
                        } else {
                            currentData.setValue((Long) currentData.getValue() + 1);
                        }
                        return Transaction.success(currentData);
                    }
                    @Override
                    public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot currentData) {
                    }
                });


            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

    }

    public void color_count() {
        surveyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                DatabaseReference objRef=surveyRef.child("color_count");
                objRef.runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData currentData) {
                        if (currentData.getValue() == null) {
                            currentData.setValue(1);
                        } else {
                            currentData.setValue((Long) currentData.getValue() + 1);
                        }
                        return Transaction.success(currentData);
                    }
                    @Override
                    public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot currentData) {
                    }
                });


            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

    }

    public String findCategory(Double v, Double a) {
        String adj[] = {"aroused", "excited", "delighted", "happy", "pleased", "serene", "relaxed", "sleepy", "droopy", "bored", "depressed", "miserable", "frustrated", "distressed", "afraid", "tense"};
        Double valence[] = {5.95, 8.11, 7.74, 8.47, 7.82, 6.9, 7.25, 4.36, 4.0, 2.95, 2.27, 2.6, 2.55, 3.38, 2.25, 2.75};
        Double arousal[] = {7.3, 6.43, 5.0, 6.05, 4.25, 4.67, 2.49, 3.04, 3.18, 3.65, 4.25, 5.06, 5.4, 6.28, 5.12, 5.32};
        Double min_dis = (v - valence[0]) * (v - valence[0]) + (a - arousal[0]) * (a - arousal[0]);
        int categoryNumber = 0;

        for (int i = 1; i < 16; i++) {
            Double v_dis = (v - valence[i]) * (v - valence[i]);
            Double a_dis = (a - arousal[i]) * (a - arousal[i]);
            Double dis = v_dis + a_dis;
            if(dis < min_dis) {
                min_dis = dis;
                categoryNumber = i;
            }
        }

        return adj[categoryNumber];
    }

   public void find_music(String adj_final){
        items.clear();
        recyclerView.setVisibility(View.VISIBLE);
        SQLite helper;
        SQLiteDatabase db;
        helper = new SQLite(this);
        Cursor cursor;

        String title;
        String performer;
        String genre;
        int count=0;
        db = helper.getReadableDatabase();

        cursor = db.rawQuery("SELECT title,performer,genre FROM music_to_value_final where word='"+adj_final+"' order by random();",null);
        while(cursor.moveToNext()&&count<5) {
            title=cursor.getString(0);
            performer=cursor.getString(1);
            genre=cursor.getString(2);
            Log.d("노래노래",title+" "+performer+" "+genre);
            SongItem item = new SongItem(title, performer,genre);
            itemArrayList.add(item);
            items.add(item);
            adapter.notifyDataSetChanged();
            count++;

            //recommend song 저장
            myRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String Title = item.getTitle();
                    String Performer = item.getPerformer();
                    myRef.child(Title).child("performer").setValue(Performer);

                    //노래 추천된 count
                    DatabaseReference upRef = myRef.child(Title).child("count");
                    upRef.runTransaction(new Transaction.Handler() {
                        @Override
                        public Transaction.Result doTransaction(MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue((Long) currentData.getValue() + 1);
                            }
                            return Transaction.success(currentData);
                        }
                        @Override
                        public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot currentData) {
                        }
                    });

                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
         }

        Log.d("item,find",""+items.size());


    }

}
