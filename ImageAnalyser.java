package kz.naqty.onim.view.ui.scan.mlkit;

import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ThumbnailUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.ImageConvertUtils;

import java.util.ArrayList;
import java.util.List;

public class ImageAnalyser implements ImageAnalysis.Analyzer {

    private FragmentManager fragmentManager;
    private BarcodeScannerOptions barcodeScannerOptions;
    private ScanInterface scanInterface;
    private String codeType;
    private ScanFragment fragment;
    private Task<List<Barcode>> result;
    private boolean active = true;
    private int analyzedImages = 0;

    public ImageAnalyser(FragmentManager fragmentManager, BarcodeScannerOptions barcodeScannerOptions, ScanInterface scanInterface, ScanFragment fragment) {
        this.fragmentManager = fragmentManager;
        this.barcodeScannerOptions = barcodeScannerOptions;
        this.scanInterface = scanInterface;
        this.codeType = ScanType.DATAMATRIX;
        this.fragment = fragment;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        scanbarcode(image);
    }

    private Bitmap inverseBitmapColors(Bitmap bitmap) {
        Bitmap invBitmap = bitmap.copy(bitmap.getConfig(), true);
        for (int i = 0; i < invBitmap.getWidth(); i++) {
            for (int j = 0; j < invBitmap.getHeight(); j++) {
                invBitmap.setPixel(i, j, invBitmap.getPixel(i, j) ^ 0x00ffffff);
            }
        }
        return invBitmap;
    }

    private void scanbarcode(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError") Bitmap bitmap = BitmapUtils.getBitmap(image);
        assert bitmap != null;
        Bitmap cropped = Bitmap.createBitmap(bitmap, bitmap.getWidth() / 4, (bitmap.getHeight() / 2) - bitmap.getWidth() / 4, bitmap.getWidth() / 2, bitmap.getWidth() / 2);
        Bitmap inverted = inverseBitmapColors(cropped);
        InputImage invertedImage = InputImage.fromBitmap(inverted, image.getImageInfo().getRotationDegrees());
        BarcodeScanner scanner = BarcodeScanning.getClient(barcodeScannerOptions);
        scanner.process(invertedImage)
                .addOnSuccessListener(barcodes -> {
                    if (active) {
                        readerBarcodeData(barcodes);
                    }
                })
                .addOnFailureListener(e -> {
                })
                .addOnCompleteListener(task -> image.close());

    }

    private void readerBarcodeData(List<Barcode> barcodes) {
        String code = "";
        for (Barcode barcode: barcodes) {

            code = barcode.getRawValue();
        }
        assert code != null;
        if (!code.isEmpty()) {
            scanInterface.onScanned(code, codeType);
            active = false;
        }
        Log.e("ASD", code);
    }

    public void setScanOptions(BarcodeScannerOptions barcodeScanOptions, String codeType) {
        this.barcodeScannerOptions = barcodeScanOptions;
        this.codeType = codeType;
    }

    public void pause() {
        active = false;
    }

    public void resume() {
        active = true;
    }
}
