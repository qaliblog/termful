package com.termful.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.termful.R;
import com.termful.app.TermuxInstaller;
import com.termful.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public class DistributionSelectorActivity extends Activity {
    
    private static final String LOG_TAG = "DistributionSelector";
    
    public static final String EXTRA_DISTRIBUTION = "distribution";
    public static final String EXTRA_DOWNLOAD_URL = "download_url";
    
    private ListView distributionListView;
    private Button downloadButton;
    private Button skipButton;
    private String selectedDistribution = null;
    private String selectedDownloadUrl = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distribution_selector);
        
        initializeViews();
        setupDistributionList();
        setupButtons();
    }
    
    private void initializeViews() {
        distributionListView = findViewById(R.id.distribution_list);
        downloadButton = findViewById(R.id.download_button);
        skipButton = findViewById(R.id.skip_button);
        
        // Disable download button initially
        downloadButton.setEnabled(false);
    }
    
    private void setupDistributionList() {
        List<DistributionInfo> distributions = getAvailableDistributions();
        
        ArrayAdapter<DistributionInfo> adapter = new ArrayAdapter<DistributionInfo>(this, 
            android.R.layout.simple_list_item_single_choice, distributions) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                DistributionInfo distro = distributions.get(position);
                
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setText(distro.getName());
                
                // Add subtitle with description
                TextView subtitle = (TextView) view.findViewById(android.R.id.text2);
                if (subtitle == null) {
                    subtitle = new TextView(this.getContext());
                    subtitle.setTextSize(12);
                    subtitle.setTextColor(getContext().getResources().getColor(android.R.color.darker_gray));
                    ((android.widget.LinearLayout) view).addView(subtitle);
                }
                subtitle.setText(distro.getDescription());
                
                return view;
            }
        };
        
        distributionListView.setAdapter(adapter);
        distributionListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        distributionListView.setOnItemClickListener((parent, view, position, id) -> {
            DistributionInfo selectedDistro = distributions.get(position);
            selectedDistribution = selectedDistro.getName();
            selectedDownloadUrl = selectedDistro.getDownloadUrl();
            downloadButton.setEnabled(true);
            
            Logger.logInfo(LOG_TAG, "Selected distribution: " + selectedDistribution);
        });
    }
    
    private void setupButtons() {
        downloadButton.setOnClickListener(v -> {
            if (selectedDistribution != null && selectedDownloadUrl != null) {
                showDownloadConfirmation();
            }
        });
        
        skipButton.setOnClickListener(v -> {
            // Skip distribution download and proceed with minimal bootstrap
            proceedWithMinimalBootstrap();
        });
    }
    
    private void showDownloadConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Download " + selectedDistribution + "?")
               .setMessage("This will open your browser to download the " + selectedDistribution + 
                          " rootfs. After downloading, you can select the file in the next step.")
               .setPositiveButton("Download", (dialog, which) -> {
                   openDownloadUrl();
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void openDownloadUrl() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(selectedDownloadUrl));
            startActivity(intent);
            
            // Show file picker dialog
            showFilePickerDialog();
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to open download URL: " + e.getMessage());
            Toast.makeText(this, "Failed to open download URL", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showFilePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Downloaded File")
               .setMessage("After downloading, use the file picker to select the " + selectedDistribution + 
                          " rootfs file (.tar.gz or .zip)")
               .setPositiveButton("Select File", (dialog, which) -> {
                   openFilePicker();
               })
               .setNegativeButton("Skip", (dialog, which) -> {
                   proceedWithMinimalBootstrap();
               })
               .show();
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(intent, 1001);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to open file picker: " + e.getMessage());
            Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                // Process the selected file
                processSelectedFile(fileUri);
            }
        }
    }
    
    private void processSelectedFile(Uri fileUri) {
        // TODO: Implement file processing logic
        // This would involve copying the file to the appropriate location
        // and updating the installer to use the custom distribution
        
        Toast.makeText(this, "File selected: " + fileUri.toString(), Toast.LENGTH_SHORT).show();
        
        // For now, proceed with the installation
        proceedWithCustomDistribution(fileUri);
    }
    
    private void proceedWithCustomDistribution(Uri fileUri) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_DISTRIBUTION, selectedDistribution);
        resultIntent.putExtra(EXTRA_DOWNLOAD_URL, fileUri.toString());
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    private void proceedWithMinimalBootstrap() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_DISTRIBUTION, "minimal");
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    private List<DistributionInfo> getAvailableDistributions() {
        List<DistributionInfo> distributions = new ArrayList<>();
        
        distributions.add(new DistributionInfo(
            "Alpine Linux",
            "Lightweight, security-oriented Linux distribution (~5MB)",
            "https://alpinelinux.org/downloads/"
        ));
        
        distributions.add(new DistributionInfo(
            "Debian",
            "Stable, universal Linux distribution (~50MB)",
            "https://www.debian.org/distrib/"
        ));
        
        distributions.add(new DistributionInfo(
            "Ubuntu",
            "Popular Linux distribution with extensive software support (~100MB)",
            "https://ubuntu.com/download"
        ));
        
        distributions.add(new DistributionInfo(
            "Arch Linux",
            "Lightweight and flexible Linux distribution (~200MB)",
            "https://archlinux.org/download/"
        ));
        
        distributions.add(new DistributionInfo(
            "Fedora",
            "Cutting-edge Linux distribution (~500MB)",
            "https://getfedora.org/"
        ));
        
        return distributions;
    }
    
    private static class DistributionInfo {
        private final String name;
        private final String description;
        private final String downloadUrl;
        
        public DistributionInfo(String name, String description, String downloadUrl) {
            this.name = name;
            this.description = description;
            this.downloadUrl = downloadUrl;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getDownloadUrl() { return downloadUrl; }
        
        @Override
        public String toString() {
            return name;
        }
    }
}