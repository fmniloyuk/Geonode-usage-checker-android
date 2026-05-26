package ee.codenixlabs.geonodeusage;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "geonode_accounts";
    private static final String ACCOUNTS_KEY = "accounts_json";
    private static final String USAGE_URL = "https://monitor.geonode.com/monitor-light/proxies";

    private final List<Account> accounts = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private Spinner accountSpinner;
    private EditText accountNameInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView resultText;
    private ProgressBar progressBar;
    private Button checkButton;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        buildUi();
        loadAccounts();
        refreshSpinner();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("GeoNode Usage Checker");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(15, 23, 42));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Save multiple GeoNode accounts and check bandwidth usage.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        LinearLayout card = cardLayout();
        root.addView(card);

        TextView selectLabel = label("Select saved account");
        card.addView(selectLabel);
        accountSpinner = new Spinner(this);
        card.addView(accountSpinner, matchWrap());

        accountNameInput = editText("Account name", InputType.TYPE_CLASS_TEXT);
        usernameInput = editText("GEONODE_USERNAME", InputType.TYPE_CLASS_TEXT);
        passwordInput = editText("GEONODE_PASSWORD", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        card.addView(label("Account name"));
        card.addView(accountNameInput, matchWrap());
        card.addView(label("GeoNode username"));
        card.addView(usernameInput, matchWrap());
        card.addView(label("GeoNode password"));
        card.addView(passwordInput, matchWrap());

        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setPadding(0, dp(14), 0, dp(8));
        card.addView(buttonsRow);

        Button saveButton = button("Save / Update");
        Button deleteButton = button("Delete");
        buttonsRow.addView(saveButton, weightWrap());
        buttonsRow.addView(deleteButton, weightWrap());

        checkButton = button("Check Usage Statistics");
        card.addView(checkButton, matchWrap());

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(12), 0, dp(4));
        card.addView(progressBar, progressParams);

        resultText = new TextView(this);
        resultText.setText("No usage checked yet.");
        resultText.setTextSize(15);
        resultText.setTextColor(Color.rgb(15, 23, 42));
        resultText.setPadding(dp(12), dp(12), dp(12), dp(12));
        resultText.setBackgroundColor(Color.rgb(241, 245, 249));
        card.addView(resultText, matchWrap());

        setContentView(scrollView);

        saveButton.setOnClickListener(v -> saveOrUpdateAccount());
        deleteButton.setOnClickListener(v -> deleteSelectedAccount());
        checkButton.setOnClickListener(v -> checkUsage());

        accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < accounts.size()) {
                    Account account = accounts.get(position);
                    accountNameInput.setText(account.name);
                    usernameInput.setText(account.username);
                    passwordInput.setText(account.password);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private LinearLayout cardLayout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(1, Color.rgb(226, 232, 240));
        card.setBackground(bg);
        return card;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.rgb(51, 65, 85));
        tv.setPadding(0, dp(12), 0, dp(4));
        return tv;
    }

    private EditText editText(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextSize(15);
        et.setPadding(dp(10), 0, dp(10), 0);
        return et;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadAccounts() {
        accounts.clear();
        String raw = prefs.getString(ACCOUNTS_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                accounts.add(new Account(
                        obj.optString("name"),
                        obj.optString("username"),
                        obj.optString("password")
                ));
            }
        } catch (Exception ignored) {}
    }

    private void persistAccounts() {
        JSONArray arr = new JSONArray();
        try {
            for (Account a : accounts) {
                JSONObject obj = new JSONObject();
                obj.put("name", a.name);
                obj.put("username", a.username);
                obj.put("password", a.password);
                arr.put(obj);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(ACCOUNTS_KEY, arr.toString()).apply();
    }

    private void refreshSpinner() {
        List<String> names = new ArrayList<>();
        for (Account a : accounts) {
            names.add(a.name + " (" + a.username + ")");
        }
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        accountSpinner.setAdapter(spinnerAdapter);
    }

    private void saveOrUpdateAccount() {
        String name = accountNameInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            toast("Please fill account name, username, and password.");
            return;
        }

        int selected = accountSpinner.getSelectedItemPosition();
        if (selected >= 0 && selected < accounts.size()) {
            accounts.set(selected, new Account(name, username, password));
        } else {
            accounts.add(new Account(name, username, password));
        }
        persistAccounts();
        refreshSpinner();
        toast("Account saved.");
    }

    private void deleteSelectedAccount() {
        int selected = accountSpinner.getSelectedItemPosition();
        if (selected >= 0 && selected < accounts.size()) {
            accounts.remove(selected);
            persistAccounts();
            refreshSpinner();
            accountNameInput.setText("");
            usernameInput.setText("");
            passwordInput.setText("");
            toast("Account deleted.");
        } else {
            toast("No account selected.");
        }
    }

    private void checkUsage() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            toast("Please select or enter GeoNode credentials first.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        checkButton.setEnabled(false);
        resultText.setText("Checking usage statistics...");

        executor.execute(() -> {
            String output;
            try {
                output = fetchUsage(username, password);
            } catch (Exception e) {
                output = "Failed to fetch usage statistics.\n\n" + e.getMessage();
            }
            String finalOutput = output;
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                checkButton.setEnabled(true);
                resultText.setText(finalOutput);
            });
        });
    }

    private String fetchUsage(String username, String password) throws Exception {
        URL url = new URL(USAGE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");

        String credentials = username + ":" + password;
        String encoded = Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        conn.setRequestProperty("Authorization", "Basic " + encoded);

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);

        if (status != 200) {
            return "Failed to fetch GeoNode usage statistics.\n\nStatus code: " + status + "\nResponse: " + body;
        }

        JSONObject root = new JSONObject(body);
        JSONObject bandwidth = root
                .optJSONObject("data")
                .optJSONObject("bandwidth")
                .optJSONObject("data");

        if (bandwidth == null) {
            return "Usage response received, but bandwidth data was not found.\n\nRaw response:\n" + body;
        }

        long usedBytes = bandwidth.optLong("default", 0L);
        double usedGb = usedBytes / Math.pow(1024, 3);
        double totalBandwidthGb = bandwidth.optDouble("totalBandwidthInGB", 0.0);
        double currentFastBandwidth = bandwidth.optDouble("currentFastBandwidth", 0.0);
        double remainingGb = Math.max(0.0, totalBandwidthGb - usedGb);
        double usedPercent = totalBandwidthGb > 0 ? (usedGb / totalBandwidthGb) * 100.0 : 0.0;

        return "Geonode Usage Statistics\n" +
                "------------------------\n" +
                "Username: " + username + "\n" +
                "Used bandwidth bytes: " + usedBytes + "\n" +
                "Used bandwidth GB: " + format(usedGb) + " GB\n" +
                "Total bandwidth from API: " + format(totalBandwidthGb) + " GB\n" +
                "Remaining bandwidth: " + format(remainingGb) + " GB\n" +
                "Usage percentage: " + format(usedPercent) + "%\n" +
                "Current fast bandwidth: " + format(currentFastBandwidth);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class Account {
        final String name;
        final String username;
        final String password;

        Account(String name, String username, String password) {
            this.name = name;
            this.username = username;
            this.password = password;
        }
    }
}
