package com.deniscerri.ytdl;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.deniscerri.ytdl.database.DBManager;
import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;
import com.yausername.youtubedl_android.DownloadProgressCallback;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment implements View.OnClickListener{

    private boolean downloading = false;
    private View fragmentView;
    private ProgressBar progressBar;
    private String inputQuery;
    private LinearLayout linearLayout;
    private ScrollView scrollView;

    private JSONObject requestData;
    private String videoID;

    private static final String TAG = "HomeFragment";

    private ArrayList<JSONObject> resultObjects;
    private boolean hasResults = false;
    private int[] positions = {0,0};

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private DBManager dbManager;

    private final DownloadProgressCallback callback = new DownloadProgressCallback() {
        @Override
        public void onProgressUpdate(float progress, long etaInSeconds, String line) {
            requireActivity().runOnUiThread(() -> progressBar.setProgress((int) progress)
            );
        }
    };


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        compositeDisposable = new CompositeDisposable();
        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);
        initViews();

        fragmentView.setOnScrollChangeListener((view, i, i1, i2, i3) -> positions = new int[]{i,i1});

        if(hasResults){
            scrollView.post(() -> scrollView.scrollTo(positions[0], positions[1]));
            for(int i = 0; i < resultObjects.size(); i++){
                createCard(resultObjects.get(i));
            }
        }


        return fragmentView;
    }


    private void initViews() {
        linearLayout = fragmentView.findViewById(R.id.linearLayout1);
        scrollView = fragmentView.findViewById(R.id.scrollView1);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.main_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);

        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return true;
            }
        };

        menu.findItem(R.id.search).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        searchView.setQueryHint("Kërko nga YouTube");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                menu.findItem(R.id.search).collapseActionView();
                inputQuery = query.trim();
                parseQuery();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    public void handleIntent(Intent intent){
        inputQuery = intent.getStringExtra(Intent.EXTRA_TEXT);
        parseQuery();
    }

    private void parseQuery() {
        linearLayout.removeAllViews();
        resultObjects = new ArrayList<>();

        String type = "Search";
        Pattern p = Pattern.compile("^(https?)://(www.)?youtu(.be)?");
        Matcher m = p.matcher(inputQuery);

        if(m.find()){
            type = "Video";

            if (inputQuery.contains("list=")) {
                type = "Playlist";
            }
        }

        Log.e(TAG, inputQuery + " "+ type);

        try {
            switch (type) {
                case "Search": {
                    Thread thread = new Thread(() -> {
                        try {
                            requestData =  makeRequest("https://ytdl.deniscerri.repl.co/search?Query=" + inputQuery);
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();

                    JSONArray dataArray = requestData.getJSONArray("items");
                    for(int i = 0; i < dataArray.length(); i++){
                        JSONObject element = dataArray.getJSONObject(i);
                        JSONObject snippet = element.getJSONObject("snippet");
                        if(element.getJSONObject("id").getString("kind").equals("youtube#video")){
                            hasResults = true;

                            videoID = element.getJSONObject("id").getString("videoId");
                            snippet.put("videoID", videoID);
                            snippet = fixThumbnail(snippet);
                            resultObjects.add(snippet);
                            createCard(snippet);
                        }
                    }
                    break;
                }case "Video": {
                    String[] el = inputQuery.split("/");
                    inputQuery = el[el.length -1];

                    if(inputQuery.contains("watch?v=")){
                        inputQuery = inputQuery.substring(8);
                    }

                    Thread thread = new Thread(() -> {
                        try {
                            requestData = makeRequest("https://ytdl.deniscerri.repl.co/video?id=" + inputQuery).getJSONArray("items").getJSONObject(0);
                            videoID = requestData.getString("id");
                            requestData = requestData.getJSONObject("snippet");
                            requestData.put("videoID", videoID);
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();
                    
                    hasResults = true;
                    resultObjects.add(requestData);
                    requestData = fixThumbnail(requestData);
                    createCard(requestData);
                    break;
                }case "Playlist": {
                    inputQuery = inputQuery.split("list=")[1];
                    Thread thread = new Thread(() -> {
                        try {
                            requestData = makeRequest("https://ytdl.deniscerri.repl.co/ytPlaylist?id=" + inputQuery);
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();

                    JSONArray dataArray = requestData.getJSONArray("items");
                    hasResults = true;
                    for(int i = 0; i < dataArray.length(); i++){
                        JSONObject element = dataArray.getJSONObject(i);
                        JSONObject snippet = element.getJSONObject("snippet");
                        videoID = element.getString("id");
                        if(snippet.getJSONObject("resourceId").getString("kind").equals("youtube#video")){
                            snippet.put("videoID", videoID);
                            resultObjects.add(snippet);
                            snippet = fixThumbnail(snippet);
                            createCard(snippet);
                        }
                    }
                    break;
                }
            }
        }catch(Exception e){
           Log.e(TAG, e.toString());
        }
    }

    private JSONObject fixThumbnail(JSONObject o){
        String imageURL = "";
        try{
            JSONObject thumbs = o.getJSONObject("thumbnails");
            imageURL = thumbs.getJSONObject("maxres").getString("url");
        }catch(Exception e){
            try {
                JSONObject thumbs = o.getJSONObject("thumbnails");
                imageURL = thumbs.getJSONObject("high").getString("url");
            }catch(Exception ignored){}

        }

        try{
            o.put("thumb", imageURL);
        }catch(Exception ignored){}


        return o;
    }

    private JSONObject makeRequest(String query){
        Log.e(TAG, query);
        BufferedReader reader;
        String line;
        StringBuilder responseContent = new StringBuilder();
        HttpURLConnection conn;
        JSONObject json = new JSONObject();

        try{
            URL url = new URL(query);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(5000);

            if(conn.getResponseCode() < 300){
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while((line = reader.readLine()) != null){
                    responseContent.append(line);
                }

                json = new JSONObject(responseContent.toString());
                if(json.has("error")){
                    throw new Exception();
                }
            }
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }



        return json;
    }

    private int getDp(int value){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void createCard(JSONObject info){

        RelativeLayout r = new RelativeLayout(getContext());
        r.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        r.getLayoutParams().height = getDp(230);

        CardView card = new CardView(requireContext());
        card.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(50);
        card.setCardElevation(10);
        card.setMaxCardElevation(12);
        card.setPreventCornerOverlap(true);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));

        // THUMBNAIL ----------------------------------

        ImageView thumbnail = new ImageView(getContext());
        thumbnail.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        String imageURL= "";

        try {
            imageURL = info.getString("thumb");
        }catch (Exception ignored){}

        Picasso.get().load(imageURL).into(thumbnail);
        thumbnail.setAdjustViewBounds(false);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // TITLE  ----------------------------------
        TextView videoTitle = new TextView(getContext());
        videoTitle.setLayoutParams(new RelativeLayout.LayoutParams(getDp(300), getDp(100)));
        int padding = getDp(20);
        videoTitle.setPadding(padding, padding, padding, padding);
        String title;
        try{
            title = info.getString("title");
        }catch(Exception e){
            title = "";
        }

        if(title.length() > 50){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);
        videoTitle.setTextSize(getDp(5));
        videoTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoTitle.setShadowLayer(1, 1, 1, ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));
        videoTitle.setTypeface(null, Typeface.BOLD);
        videoTitle.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        // AUTHOR ----------------------------------

        TextView videoAuthor = new TextView(getContext());
        videoAuthor.setGravity(Gravity.BOTTOM);
        videoAuthor.setLayoutParams(new RelativeLayout.LayoutParams(getDp(200), getDp(200)));
        videoAuthor.setPadding(getDp(20), 0, 0, getDp(10));
        videoAuthor.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        String author;
        try{
            author = info.getString("channelTitle");
        }catch(Exception e){
            author = "";
        }
        videoAuthor.setText(author);
        videoAuthor.setTextSize(getDp(3));
        videoAuthor.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoAuthor.setShadowLayer(1, 1, 1, ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));
        videoAuthor.setTypeface(null, Typeface.BOLD);

        // BUTTONS ----------------------------------
        String videoID;
        try{
            videoID = info.getString("videoID");
        }catch(Exception e){
            videoID = "";
        }

        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        buttonLayout.setGravity(Gravity.BOTTOM);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(getDp(250), 0, getDp(20), getDp(10));

        Button musicBtn = new Button(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getDp(48), getDp(48)
        );
        params.setMargins(0,0, getDp(10), 0);
        musicBtn.setLayoutParams(params);
        //musicBtn.setIconSize(getDp(24));
        musicBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_music));
        musicBtn.setTag(videoID + "##mp3");

        Button videoBtn = new Button(requireContext());
        videoBtn.setLayoutParams(params);
        //videoBtn.setIconSize(getDp(24));
        videoBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_video));
        videoBtn.setTag(videoID + "##mp4");

        buttonLayout.addView(musicBtn);
        buttonLayout.addView(videoBtn);

        musicBtn.setOnClickListener(this);
        videoBtn.setOnClickListener(this);

        // PROGRESS BAR ----------------------------------------------------

        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, getDp(5));
        progressParams.setMargins(0, getDp(210), 0 ,0);
        progressBar.setLayoutParams(progressParams);
        progressBar.setBackgroundTintMode(PorterDuff.Mode.SRC_IN);
        progressBar.setScaleY(2);
        progressBar.setTag(videoID + "##progress");


        // Adding all layouts to the card

        card.addView(thumbnail);
        card.addView(videoTitle);
        card.addView(videoAuthor);
        card.addView(buttonLayout);
        card.addView(progressBar);

        r.addView(card);

        linearLayout.addView(r);
    }



    @Override
    public void onClick(View v) {
        //do what you want to do when button is clicked
        String viewIdName = v.getTag().toString();
        Log.e(TAG, viewIdName);
        if(viewIdName.contains("mp3") || viewIdName.contains("mp4")){
            String[] buttonData = viewIdName.split("##");
            startDownload(buttonData[0], buttonData[1]);
        }
    }





    private void startDownload(String id, String type) {
        if (downloading) {
            Toast.makeText(getContext(), "Nuk mund te filloj! Nje shkarkim tjeter është në punë!", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isStoragePermissionGranted()) {
            Toast.makeText(getContext(), "Pranoje Lejen dhe provo përsëri!", Toast.LENGTH_LONG).show();
            return;
        }
        String url = "https://www.youtube.com/watch?v=" + id;
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        File youtubeDLDir = getDownloadLocation(type);

        Log.e(TAG, youtubeDLDir.getAbsolutePath());

        if(type.equals("mp3")){
            request.addOption("--no-mtime");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");
        }else if(type.equals("mp4")){
            request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
        }
        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        progressBar = fragmentView.findViewWithTag(id+"##progress");
        progressBar.setVisibility(View.VISIBLE);

        showStart();
        downloading = true;

        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    progressBar.setProgress(100);
                    Toast.makeText(getContext(), "Shkarkimi i be me Sukses!", Toast.LENGTH_LONG).show();
                    addToHistory(id, type, new Date());
                    downloading = false;
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG,  "Deshtim ne shkarkim! :(", e);
                    Toast.makeText(getContext(), "Deshtim ne shkarkim! :(", Toast.LENGTH_LONG).show();
                    downloading = false;
                });
        compositeDisposable.add(disposable);

    }


    public void addToHistory(String id, String downloadedType, Date date){
        JSONObject videoja = null;

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        int year = cal.get(Calendar.YEAR);

        DateFormat formatter = new SimpleDateFormat("HH:mm");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = formatter.format(date);
        String downloadedTime = day + " " + month + " " + year + " " + time;

        for(JSONObject o : resultObjects){
            try{
                if(o.getString("videoID").equals(id)){
                    videoja = o;
                }
            }catch(Exception e){

            }
        }

        System.err.println(videoja);
        
        if(videoja != null){
            dbManager = new DBManager(requireContext());
            try{
                String title = videoja.getString("title");
                String author = videoja.getString("channelTitle");
                String thumb = videoja.getString("thumb");

                dbManager.shtoVideo(id, title, author, thumb, downloadedType, downloadedTime);
            }catch(Exception ignored){}
        }
    }


    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    @NonNull
    private File getDownloadLocation(String type) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String downloadsDir;
        if(type.equals("mp3")){
            downloadsDir = sharedPreferences.getString("music_path", "");
        }else{
            downloadsDir = sharedPreferences.getString("video_path", "");
        }
        File youtubeDLDir = new File(downloadsDir);
        if (!youtubeDLDir.exists()) youtubeDLDir.mkdir();
        return youtubeDLDir;
    }

    private void showStart() {
        Toast.makeText(getContext(), "Shkarkimi Filloi!", Toast.LENGTH_LONG).show();
        progressBar.setProgress(0);
    }

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }


}