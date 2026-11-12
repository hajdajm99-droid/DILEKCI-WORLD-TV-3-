
package com.dilekcitv.world;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.view.KeyEvent;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private EditText urlInput;
    private ListView channelList;

    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<String> links = new ArrayList<>();

    private static final int PICK_M3U = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createPlayer();
        createScreen();
    }

    private void createPlayer() {
        player = new ExoPlayer.Builder(this).build();

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setFocusable(true);
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 23, 43));
        root.setPadding(12, 12, 12, 12);

        TextView title = new TextView(this);
        title.setText("DİLEKÇİ WORLD TV");
        title.setTextColor(Color.CYAN);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(8, 12, 8, 12);
        root.addView(title);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        urlInput = new EditText(this);
        urlInput.setHint("M3U bağlantısını gir");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.LTGRAY);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);

        controls.addView(urlInput,
            new LinearLayout.LayoutParams(0, 55, 1));

        Button loadButton = new Button(this);
        loadButton.setText("YÜKLE");
        controls.addView(loadButton);

        Button fileButton = new Button(this);
        fileButton.setText("DOSYA");
        controls.addView(fileButton);

        root.addView(controls);

        playerView.setMinimumHeight(220);

        root.addView(playerView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2));

        TextView channelTitle = new TextView(this);
        channelTitle.setText("KANAL LİSTESİ");
        channelTitle.setTextColor(Color.CYAN);
        channelTitle.setTextSize(18);
        channelTitle.setPadding(8, 10, 8, 10);
        root.addView(channelTitle);

        channelList = new ListView(this);
        channelList.setBackgroundColor(Color.rgb(25, 43, 75));

        root.addView(channelList,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3));

        setContentView(root);

        loadButton.setOnClickListener(v -> {
            String address = urlInput.getText().toString().trim();

            if (address.isEmpty()) {
                Toast.makeText(this, "M3U adresi girin",
                    Toast.LENGTH_SHORT).show();
            } else {
                loadFromUrl(address);
            }
        });

        fileButton.setOnClickListener(v -> chooseFile());

        channelList.setOnItemClickListener((parent, view, position, id) -> {
            playChannel(position);
        });
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_M3U);
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_M3U &&
            resultCode == RESULT_OK && data != null) {

            Uri uri = data.getData();

            if (uri != null) {
                new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                getContentResolver().openInputStream(uri)));

                        parsePlaylist(reader);

                    } catch (Exception e) {
                        showMessage("Dosya okunamadı");
                    }
                }).start();
            }
        }
    }

    private void loadFromUrl(String address) {

        if (!address.startsWith("https://") &&
            !address.startsWith("http://")) {
            showMessage("Geçerli bir internet adresi girin");
            return;
        }

        showMessage("Liste yükleniyor...");

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                    new URL(address).openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty(
                    "User-Agent", "DilekciWorldTV/2.0");

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

                parsePlaylist(reader);

            } catch (Exception e) {
                showMessage("M3U listesi yüklenemedi");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void parsePlaylist(BufferedReader reader) {

        ArrayList<String> newNames = new ArrayList<>();
        ArrayList<String> newLinks = new ArrayList<>();

        try {
            String line;
            String pendingName = "İsimsiz Kanal";

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF")) {
                    int comma = line.lastIndexOf(",");

                    if (comma >= 0 && comma < line.length() - 1) {
                        pendingName = line.substring(comma + 1).trim();
                    } else {
                        pendingName = "TV Kanalı";
                    }

                } else if (!line.startsWith("#")) {

                    newNames.add(pendingName);
                    newLinks.add(line);
                    pendingName = "İsimsiz Kanal";
                }
            }

            reader.close();

            runOnUiThread(() -> {
                names.clear();
                links.clear();

                names.addAll(newNames);
                links.addAll(newLinks);

                ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, names);

                channelList.setAdapter(adapter);

                Toast.makeText(this,
                    names.size() + " kanal yüklendi",
                    Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            showMessage("Liste okunamadı");
        }
    }

    private void playChannel(int position) {

        if (position < 0 || position >= links.size()) return;

        String address = links.get(position);

        try {
            MediaItem item;

            if (address.toLowerCase().contains(".m3u8")) {
                item = new MediaItem.Builder()
                    .setUri(address)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build();
            } else {
                item = MediaItem.fromUri(address);
            }

            player.setMediaItem(item);
            player.prepare();
            player.play();

            Toast.makeText(this, names.get(position),
                Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            showMessage("Kanal açılamadı");
        }
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
            Toast.makeText(this, message,
                Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER) {

            View focused = getCurrentFocus();

            if (focused == channelList &&
                channelList.getSelectedItemPosition() >= 0) {
                playChannel(channelList.getSelectedItemPosition());
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (player != null) {
            player.release();
            player = null;
        }
    }
}
