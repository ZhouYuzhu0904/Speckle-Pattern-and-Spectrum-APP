package com.example.speclespectrumk;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.specklespectrum2.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private ImageView imagePreview;
    private Button btnGallery, btnCamera, btnAnalyze;
    private ProgressBar progressBar;
    private LineChart chartSpectrum;
    private TextView tvInfo, tvCoord;

    private Bitmap currentBitmap;
    private PyTorchClassifier classifier;
    private String currentPhotoPath;

    // 光谱参数
    private static final float START_WAVELENGTH = 520f;  // 起始波长 520nm
    private static final float END_WAVELENGTH = 720f;    // 结束波长 720nm
    private static final int SPECTRUM_POINTS = 402;      // 201个点

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "onCreate 执行了");
        initViews();
        initClassifier();
        setupListeners();
        setupChart();
        requestPermissions();
    }

    private void initViews() {
        imagePreview = findViewById(R.id.image_preview);
        btnGallery = findViewById(R.id.btn_gallery);
        btnCamera = findViewById(R.id.btn_camera);
        btnAnalyze = findViewById(R.id.btn_analyze);
        progressBar = findViewById(R.id.progress_bar);
        chartSpectrum = findViewById(R.id.chart_spectrum);
        tvInfo = findViewById(R.id.tv_info);
        tvCoord = findViewById(R.id.tv_coord);
    }

    private void initClassifier() {
        try {
            classifier = new PyTorchClassifier(this);
            tvInfo.setText("模型加载成功");
        } catch (Exception e) {
            tvInfo.setText("模型加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupListeners() {
        btnGallery.setOnClickListener(v -> openGallery());
        btnCamera.setOnClickListener(v -> openCamera());
        btnAnalyze.setOnClickListener(v -> analyzeImage());
    }

    private void setupChart() {
        // X轴设置（波长 520-720nm）
        XAxis xAxis = chartSpectrum.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setLabelCount(6, true);
        xAxis.setAxisMinimum(START_WAVELENGTH);
        xAxis.setAxisMaximum(END_WAVELENGTH);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(20f);

        // Y轴设置
        YAxis leftAxis = chartSpectrum.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(1f);

        chartSpectrum.getAxisRight().setEnabled(false);
        chartSpectrum.getDescription().setEnabled(false);
        chartSpectrum.setNoDataText("请先分析图片");
        chartSpectrum.setTouchEnabled(true);
        chartSpectrum.setDragEnabled(true);
        chartSpectrum.setScaleEnabled(true);

        // 设置点击曲线显示坐标的监听器
        chartSpectrum.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                float wavelength = e.getX();
                float intensity = e.getY();
                tvCoord.setText(String.format("波长: %.1f nm, 强度: %.4f", wavelength, intensity));
            }

            @Override
            public void onNothingSelected() {
                tvCoord.setText("点击曲线上任一点显示波长和强度");
            }
        });
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // 相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // 存储权限（根据Android版本）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = null;
        try {
            photoFile = createImageFile();
            Log.d("Camera", "文件创建成功: " + photoFile.getAbsolutePath());
        } catch (IOException ex) {
            Log.e("Camera", "创建文件失败", ex);
            Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
            return;
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            Log.d("Camera", "相机已启动");
        }
    }
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void analyzeImage() {
        if (currentBitmap == null) {
            Toast.makeText(this, "请先选择或拍摄图片", Toast.LENGTH_SHORT).show();
            return;
        }

        if (classifier == null) {
            Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(false);
        tvInfo.setText("分析中...");

        new Thread(() -> {
            try {
                float[] spectrum = classifier.predict(currentBitmap);

                runOnUiThread(() -> {
                    if (spectrum != null && spectrum.length == SPECTRUM_POINTS) {
                        updateChart(spectrum);
                        tvInfo.setText("分析完成，光谱点数: " + spectrum.length);
                    } else {
                        tvInfo.setText("分析失败：输出格式错误");
                    }
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvInfo.setText("分析失败: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                });
            }
        }).start();
    }

    private void updateChart(float[] spectrum) {
        List<Entry> entries = new ArrayList<>();

        // 计算每个点对应的波长（520nm 到 720nm，共 201 个点）
        float step = (END_WAVELENGTH - START_WAVELENGTH) / (SPECTRUM_POINTS - 1);

        for (int i = 0; i < spectrum.length; i++) {
            float wavelength = START_WAVELENGTH + i * step;
            entries.add(new Entry(wavelength, spectrum[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "光谱强度");
        dataSet.setColor(getColor(android.R.color.holo_blue_dark));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(0f);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(getColor(android.R.color.holo_blue_light));
        dataSet.setFillAlpha(100);

        LineData lineData = new LineData(dataSet);
        chartSpectrum.setData(lineData);
        chartSpectrum.invalidate();  // 刷新图表

        tvCoord.setText("点击曲线上任一点显示波长和强度");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("Camera", "=== onActivityResult 被调用 ===");
        Log.d("Camera", "requestCode: " + requestCode);
        Log.d("Camera", "resultCode: " + resultCode);
        Log.d("Camera", "data: " + data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_GALLERY && data != null) {
                // 从相册选择
                Log.d("Camera", "处理相册选择");
                Uri imageUri = data.getData();
                Log.d("Camera", "imageUri: " + imageUri);
                try {
                    currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    Log.d("Camera", "相册图片加载成功，大小: " + currentBitmap.getWidth() + "x" + currentBitmap.getHeight());
                    imagePreview.setImageBitmap(currentBitmap);
                    btnAnalyze.setEnabled(true);
                    chartSpectrum.clear();
                    tvInfo.setText("图片已加载，点击分析");
                } catch (IOException e) {
                    Log.e("Camera", "加载相册图片失败", e);
                    Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // 拍照
                Log.d("Camera", "处理拍照结果");
                Log.d("Camera", "currentPhotoPath: " + currentPhotoPath);

                if (currentPhotoPath == null) {
                    Log.e("Camera", "currentPhotoPath 为 null");
                    Toast.makeText(this, "拍照路径为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                File imgFile = new File(currentPhotoPath);
                Log.d("Camera", "文件是否存在: " + imgFile.exists());

                if (imgFile.exists()) {
                    Log.d("Camera", "文件大小: " + imgFile.length() + " 字节");

                    // 解码图片
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;  // 缩小一半
                    currentBitmap = BitmapFactory.decodeFile(currentPhotoPath, options);

                    if (currentBitmap != null) {
                        Log.d("Camera", "Bitmap 解码成功，大小: " + currentBitmap.getWidth() + "x" + currentBitmap.getHeight());
                        imagePreview.setImageBitmap(currentBitmap);
                        btnAnalyze.setEnabled(true);
                        chartSpectrum.clear();
                        tvInfo.setText("图片已加载，点击分析");
                    } else {
                        Log.e("Camera", "Bitmap 解码失败");
                        Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("Camera", "照片文件不存在");
                    Toast.makeText(this, "拍照失败，文件不存在", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.d("Camera", "resultCode 不是 RESULT_OK: " + resultCode);
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Toast.makeText(this, "拍照取消或失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要权限: " + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) {
            classifier.close();
        }
    }
}