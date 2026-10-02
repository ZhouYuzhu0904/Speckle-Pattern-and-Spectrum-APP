package com.example.speclespectrumk;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PyTorchClassifier {
    private static final String TAG = "PyTorchClassifier";
    private Module module;

    public PyTorchClassifier(Context context) {
        try {
            String modelPath = assetFilePath(context, "speckle_model.pt");
            Log.d(TAG, "模型路径: " + modelPath);
            module = Module.load(modelPath);
            Log.d(TAG, "✅ 模型加载成功");
        } catch (IOException e) {
            Log.e(TAG, "❌ 模型加载失败", e);
            throw new RuntimeException(e);
        }
    }

    private static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (FileOutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public float[] predict(Bitmap bitmap) {
        if (module == null) {
            Log.e(TAG, "模型未加载");
            return null;
        }

        try {
            // 1. 缩放图片到256x256
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true);

            // 2. 转换为灰度图
            Bitmap grayBitmap = toGrayscale(resizedBitmap);

            // 3. 创建输入数组
            float[] inputArray = new float[256 * 256];
            for (int i = 0; i < 256; i++) {
                for (int j = 0; j < 256; j++) {
                    int pixel = grayBitmap.getPixel(j, i);
                    int gray = pixel & 0xFF;
                    inputArray[i * 256 + j] = gray / 255.0f;
                }
            }

            // 4. 转换为Tensor
            Tensor inputTensor = Tensor.fromBlob(inputArray, new long[]{1, 1, 256, 256});

            // 5. 推理
            long startTime = System.currentTimeMillis();
            Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
            long endTime = System.currentTimeMillis();

            Log.d(TAG, "推理耗时: " + (endTime - startTime) + "ms");

            // 6. 返回结果
            return outputTensor.getDataAsFloatArray();

        } catch (Exception e) {
            Log.e(TAG, "推理失败", e);
            return null;
        }
    }

    private Bitmap toGrayscale(Bitmap bitmap) {
        Bitmap grayBitmap = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(grayBitmap);
        android.graphics.Paint paint = new android.graphics.Paint();
        android.graphics.ColorMatrix colorMatrix = new android.graphics.ColorMatrix();
        colorMatrix.setSaturation(0);
        android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return grayBitmap;
    }

    public void close() {
        if (module != null) {
            module = null;
        }
    }
}