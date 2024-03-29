package com.example.obdemo;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int RESULT_LOAD_IMAGE = 1;
    private ImageView imageView;
    private Executor executor = Executors.newSingleThreadExecutor();
    private ObjectDetector detector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button select = (Button) findViewById(R.id.select);
        imageView = (ImageView) findViewById(R.id.image);
        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                startActivityForResult(i, RESULT_LOAD_IMAGE);
            }
        });

        detector = new ObjectDetector("labels-cn.txt", "model.pb", getAssets());
        try {
            detector.load();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            final Bitmap originImage = BitmapFactory.decodeFile(picturePath);
            imageView.setImageBitmap(originImage);
            final ProgressDialog progress = new ProgressDialog(this);
            progress.setTitle("请等待");
            progress.setMessage("正在识别");
            progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
            progress.show();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmapInput = Bitmap.createBitmap(detector.getInputSize(), detector.getInputSize(), Bitmap.Config.ARGB_8888);
                    final Matrix originToInput = Utils.getImageTransformationMatrix(
                            originImage.getWidth(), originImage.getHeight(), detector.getInputSize(), detector.getInputSize(),
                            0, false);
                    final Canvas canvas = new Canvas(bitmapInput);
                    canvas.drawBitmap(originImage, originToInput, null);

                    final List<DetectionResult> results = detector.detect(bitmapInput, 0.6f);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.hide();
                            final Bitmap copiedImage = originImage.copy(Bitmap.Config.ARGB_8888, true);
                            final Canvas resultCanvas = new Canvas(copiedImage);
                            final Paint paint = new Paint();
                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(5.0f);

                            Paint textPaint = new Paint();
                            textPaint.setColor(Color.RED);
                            textPaint.setStyle(Paint.Style.FILL);
                            textPaint.setTextSize((float) (0.04 * copiedImage.getWidth()));
                            Matrix inputToOrigin = new Matrix();
                            originToInput.invert(inputToOrigin);
                            for (DetectionResult result : results) {
                                RectF box = result.getBox();
                                inputToOrigin.mapRect(box);
                                resultCanvas.drawRect(box, paint);
                                Rect bounds = new Rect();
                                String label = result.getLabel();
                                textPaint.getTextBounds(label, 0, label.length(), bounds);
                                int height = bounds.height();
                                resultCanvas.drawText(label, box.left + 10, box.top + height + 10, textPaint);
                            }
                            imageView.setImageBitmap(copiedImage);
                        }
                    });
                }
            });
        }
    }
}
