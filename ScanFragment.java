package kz.naqty.onim.view.ui.scan.mlkit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kz.naqty.onim.R;
import kz.naqty.onim.repository.network.contract.ScanContract;
import kz.naqty.onim.repository.network.entity.check.CheckData;
import kz.naqty.onim.repository.network.entity.check.CheckItem;
import kz.naqty.onim.repository.network.entity.check.phb.CheckRequest;
import kz.naqty.onim.repository.network.entity.check.phb.CheckResponse;
import kz.naqty.onim.repository.network.entity.check.phb.CheckerData;
import kz.naqty.onim.repository.preference.SharedPrefs;
import kz.naqty.onim.repository.preference.SharedPrefsRate;
import kz.naqty.onim.util.Utils;
import kz.naqty.onim.util.constants.Category;
import kz.naqty.onim.util.constants.ResponseCode;
import kz.naqty.onim.util.listeners.OnBackPressed;
import kz.naqty.onim.view.ui.base.BaseActivity;
import kz.naqty.onim.view.ui.error.ErrorQrFragment;
import kz.naqty.onim.view.ui.error.ErrorServerFragment;
import kz.naqty.onim.view.ui.login.sms.LoginActivity;
import kz.naqty.onim.view.ui.main.MainActivity1;
import kz.naqty.onim.view.ui.result.check.CheckPdfResultFragment;
import kz.naqty.onim.view.ui.result.check.CheckResultFragment1;
import kz.naqty.onim.view.ui.result.check.catalog.CatalogFragment;
import kz.naqty.onim.view.ui.scan.camera.info.ScanInfoBarcodeFragment;
import kz.naqty.onim.view.ui.scan.camera.ui.ScanHintView;
import kz.naqty.onim.view.ui.scan.camera.ui.Tooltip;
import retrofit2.Response;

public class ScanFragment extends Fragment implements ScanInterface, ScanContract.View, OnBackPressed {

    private ListenableFuture cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private ImageAnalyser analyser;
    private androidx.camera.core.Camera camera;

    private CoordinatorLayout coordinatorLayout;
    private AppCompatImageView ivScanTabDatamatrix;
    private AppCompatImageView ivScanTabBarcode;
    private AppCompatImageView ivScanTabPdf;
    private AppCompatTextView tvScanHintMoveCamera;
    private ContentLoadingProgressBar pbProgress;
    private AppCompatImageView ivScanCursor;
    private AppCompatImageView ivScanClose;
    private AppCompatImageView ivScanFlash;
    private AppCompatImageView ivScanInfo;
    private ImageView ivImage;
    private CoordinatorLayout llScanCursor;
    private BottomAppBar bottomBar;
    private Tooltip tooltip;
    private boolean flashOn = false;

    private FirebaseAnalytics analytics;
    private ScanContract.Presenter presenter;
    private String scanType;

    private CamcorderProfile camProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
    private ProcessCameraProvider processCameraProvider;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_ui, container, false);
        presenter = new ScanFragmentPresenter(this);
        coordinatorLayout = view.findViewById(R.id.coordinator);
        previewView = view.findViewById(R.id.previewview);
        ivScanTabDatamatrix = view.findViewById(R.id.iv_scan_tab_mark);
        ivScanTabBarcode = view.findViewById(R.id.iv_scan_tab_catalog);
        ivScanTabPdf = view.findViewById(R.id.iv_scan_tab_pdf);
        ivScanCursor = view.findViewById(R.id.iv_scan_cursor);
        tvScanHintMoveCamera = view.findViewById(R.id.tv_scan_hint_move_camera);
        pbProgress = view.findViewById(R.id.pb_scan_progress);
        ivScanClose = view.findViewById(R.id.iv_btn_scan_close);
        ivScanFlash = view.findViewById(R.id.iv_btn_scan_flash);
        ivScanInfo = view.findViewById(R.id.iv_scan_info);
        llScanCursor = view.findViewById(R.id.ll_scan_cursor);
        ivImage = view.findViewById(R.id.iv_image);
        bottomBar = view.findViewById(R.id.bar);

        analytics = FirebaseAnalytics.getInstance(requireContext());
        tooltip = new Tooltip(requireContext(), coordinatorLayout);

        requireActivity().getWindow().setFlags(1024, 1024);
        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity());
        try {
            processCameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        cameraProviderFuture.addListener(() -> {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != (PackageManager.PERMISSION_GRANTED)) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    bindpreview();
                }
        }, ContextCompat.getMainExecutor(requireContext()));

        ivScanTabDatamatrix.setOnClickListener(view12 -> {
            setTabDatamatrix();
        });

        ivScanTabBarcode.setOnClickListener(view13 -> {
            setTabBarcode();
        });
        ivScanTabPdf.setOnClickListener(view14 -> {
            setTabPdf();
        });

        ivScanClose.setOnClickListener(view1 -> (requireActivity()).onBackPressed());

        ivScanFlash.setOnClickListener(view15 -> {
            toggleFlash();
        });

        ivScanInfo.setOnClickListener(view16 -> {
            if (scanType.equals(ScanType.DATAMATRIX)) {
                ((BaseActivity) requireActivity()).addFragment(new ScanInfoBarcodeFragment());
            }
            else if (scanType.equals(ScanType.PDF_417)) {
                ((BaseActivity) requireActivity()).addFragment(new ScanInfoBarcodeFragment());
            }
            else if (scanType.equals(ScanType.BARCODE)) {
                ((BaseActivity) requireActivity()).addFragment(new ScanInfoBarcodeFragment());
            }
        });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTabDatamatrix();
    }

    private void setTabDatamatrix() {
        camProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        BarcodeScannerOptions barcodeScanOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_DATA_MATRIX
                ).build();
        if (analyser == null)
            analyser = new ImageAnalyser(getParentFragmentManager(), barcodeScanOptions, this, this);
        else
            analyser.setScanOptions(barcodeScanOptions, ScanType.DATAMATRIX);
        scanType = ScanType.DATAMATRIX;

        hideProgressBar();
        bottomBar.performShow();
        showCursor();
        setTabBackground(ivScanTabDatamatrix);
        tvScanHintMoveCamera.setVisibility(View.VISIBLE);
        tvScanHintMoveCamera.setText(Utils.getString(R.string.scanner_tab_mark_hint, requireContext()));
        tooltip.show(ivScanTabDatamatrix, Utils.getString(R.string.scanner_tab_mark, requireContext()));

        showHint(coordinatorLayout, ScanHintView.ScanHintType.MARK);
        bindpreview();
    }

    public void image(Bitmap bitmap) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ivImage.setImageBitmap(bitmap);
            }
        });
    }

    private void setTabBarcode() {
        camProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        BarcodeScannerOptions barcodeScanOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_EAN_13
                ).build();
        analyser.setScanOptions(barcodeScanOptions, ScanType.BARCODE);
        scanType = ScanType.BARCODE;

        hideProgressBar();
        bottomBar.performShow();
        showCursor();
        setTabBackground(ivScanTabBarcode);
        tvScanHintMoveCamera.setVisibility(View.VISIBLE);
        tvScanHintMoveCamera.setText(Utils.getString(R.string.scanner_tab_barcode_hint, requireContext()));
        tooltip.show(ivScanTabBarcode, Utils.getString(R.string.scanner_tab_barcode, requireContext()));

        showHint(coordinatorLayout, ScanHintView.ScanHintType.BARCODE);
        bindpreview();
    }

    private void setTabPdf() {
        camProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        BarcodeScannerOptions barcodeScanOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_PDF417
                ).build();
        analyser.setScanOptions(barcodeScanOptions, ScanType.PDF_417);
        scanType = ScanType.PDF_417;

        hideProgressBar();
        bottomBar.performShow();
        showCursor();
        setTabBackground(ivScanTabPdf);
        tvScanHintMoveCamera.setVisibility(View.VISIBLE);
        tvScanHintMoveCamera.setText(Utils.getString(R.string.scanner_tab_pdf_hint, requireContext()));
        tooltip.show(ivScanTabPdf, Utils.getString(R.string.scanner_tab_pdf417, requireContext()));

        showHint(coordinatorLayout, ScanHintView.ScanHintType.PDF417);
        bindpreview();
    }

    private void toggleFlash() {
        CameraControl cameraControl = camera.getCameraControl();
        cameraControl.enableTorch(flashOn);
        flashOn = !flashOn;
        if (flashOn)
            ivScanFlash.setImageResource(R.drawable.ic_scan_flash);
        else
            ivScanFlash.setImageResource(R.drawable.ic_scan_flash_pressed);
    }

    private ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> {
                if (result) {
                    bindpreview();
                }
            }
    );

    private void bindpreview() {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageCapture imageCapture = new ImageCapture.Builder().build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(camProfile.videoFrameHeight, camProfile.videoFrameWidth))  //480 320
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, analyser);
        processCameraProvider.unbindAll();
        camera = processCameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    @Override
    public void onScanned(String code, String codeType) {
        analyser.pause();
        showProgressBar();
        Bundle bundle = new Bundle();
        bundle.putString("code_type", codeType);
        analytics.logEvent("scanning", bundle);
        Log.e("ASD", "codeType = " + codeType);
        if (!codeType.equalsIgnoreCase("qr")) {
            Log.e("ASD", "if1");
            if (!codeType.equals("pdf417")) {
                Log.e("ASD", "if2");
                Log.e("ASD", code);
                boolean isGs1DataCarrier;

                if (code != null && code.length() != 0 && code.charAt(0) == '\u001d') {
                    isGs1DataCarrier = true;
                    code = code.substring(1);
                } else {
                    isGs1DataCarrier = false;
                }

                presenter.checkCode(getCheckData(code, codeType, isGs1DataCarrier));
            }
            else {
                Log.e("ASD", "if3");
                presenter.checkPdf(getPbfCheckData(code));
            }
        }
        else {
            Log.e("ASD", "if4");
            ((BaseActivity) requireActivity()).addFragment(new ErrorQrFragment());
        }
    }

    private CheckData getCheckData(String code, String codeType, boolean isGs1DataCarrier) {
        CheckData data = new CheckData();
        data.setCode(code);
        data.setCodeType(codeType);
        if (Utils.isUserAuthorized()) {
            data.setSid(SharedPrefs.getPrefSid());
            data.setUserId(SharedPrefs.getPrefUserId());
            data.setGs1DataCarrier(isGs1DataCarrier);
        }
        return data;
    }

    private CheckRequest getPbfCheckData(String code) {
        ArrayList<Integer> location = new ArrayList<>();
        location.add(0);
        location.add(0);
        return new CheckRequest(code, "", "", "1",
                new CheckerData("string", "string", "string", "string"),
                location);
    }

    private void showHint(View view, ScanHintView.ScanHintType type) {
        ScanHintView hint = new ScanHintView(view.getContext(), ((ViewGroup) view));
        hint.setOnOkClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hint.removeHint();
                showCursor();
                tvScanHintMoveCamera.setVisibility(View.VISIBLE);
            }
        });
        if (hint.show(type)) {
            hideCursor();
            tvScanHintMoveCamera.setVisibility(View.GONE);
        }
    }

    private void setTabBackground(AppCompatImageView view) {
        ivScanTabDatamatrix.setColorFilter(null);
        ivScanTabDatamatrix.setBackgroundResource(0);
        ivScanTabBarcode.setColorFilter(null);
        ivScanTabBarcode.setBackgroundResource(0);
        ivScanTabPdf.setColorFilter(null);
        ivScanTabPdf.setBackgroundResource(0);

        view.setColorFilter(Utils.getColor(requireContext(), R.color.colorWhite), PorterDuff.Mode.SRC_IN);
        view.setBackgroundResource(R.color.colorDarkBlue);
    }

    private void showProgressBar() {
        tvScanHintMoveCamera.setVisibility(View.INVISIBLE);
        pbProgress.show();
    }

    private void hideProgressBar() {
        pbProgress.hide();
    }

    @Override
    public boolean onBackPressed() {
        analyser.pause();
        if (requireActivity() instanceof MainActivity1)
            ((MainActivity1)requireActivity()).refreshHistoryFragmentIfShowed();
        return true;
    }

    interface OnCursorDefaultListener {
        void onCursorChanged();
    }

    private void setCursorChecked(OnCursorDefaultListener listener) {
        ivScanCursor.setImageResource(R.drawable.ic_scan_check_ok);
        new Handler().postDelayed((Runnable) () -> {
            setCursorDefault();
            listener.onCursorChanged();
        }, 500);
    }

    private void setCursorError(OnCursorDefaultListener listener) {
        ivScanCursor.setImageResource(R.drawable.ic_scan_check_error);
        new Handler().postDelayed((Runnable) () -> {
            setCursorDefault();
            listener.onCursorChanged();
        }, 1700);
    }

    private void setCursorDefault() {
        ivScanCursor.setImageResource(R.drawable.ic_scan_cursor);
    }

    private void showCursor() {
        showViewAlpha(llScanCursor);
    }

    private void hideCursor() {
        hideViewAlpha(llScanCursor);
    }

    private void hideViewAlpha(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }

        Animation anim = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_fade_out);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(anim);
    }

    private void showViewAlpha(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
            view.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.anim_fade_in));
        }
    }

    private void pauseScanning() {
        analyser.pause();
    }

    private void resumeScanning() {
        analyser.resume();
    }

    @Override
    public void onCheckResponse(@Nullable Object data, @NonNull String code) {
        hideProgressBar();
        setCursorChecked(() -> {
            if (data instanceof CheckItem) {
                onCheckResponseCheckItem((CheckItem) data, code);
            } else if (data instanceof CheckResponse) {
                onPdfCheckResponseCheckItem((CheckResponse) data, code);
            }
        });
    }

    private void onCheckResponseCheckItem(CheckItem data, String code) {
        if (data.getCheckResult() && data.getCodeFounded()) {
            new SharedPrefsRate(requireContext()).traceCheckResultSuccess();
        }
        if (!data.getCodeFounded()){
            Bundle bundle = new Bundle();
            bundle.putString("code", code);
            bundle.putString("error_code", "not_found");
            analytics.logEvent("error_code", bundle);
        }
        ((BaseActivity)requireActivity()).addFragment(getFragmentByCategory(data));
    }

    private Fragment getFragmentByCategory(CheckItem data){
        if (Objects.equals(data.getCategory(), Category.GTIN)) {
            return CatalogFragment.newInstance(Objects.requireNonNull(data.getCatalogData()).get(0));
        } else {
            return CheckResultFragment1.newInstance(data);
        }
    }

    private Fragment getFragmentPdf(CheckResponse data) {
        return CheckPdfResultFragment.newInstance(data);
    }

    private void onPdfCheckResponseCheckItem(CheckResponse data, String code) {
        if (data.getErrors() != null){
            Bundle bundle = new Bundle();
            bundle.putString("code", code);
            bundle.putString("error_code", "not_found");
            analytics.logEvent("error_code", bundle);
        }
        ((BaseActivity) requireActivity()).addFragment(getFragmentPdf(data));
    }

    private int getCheckErrorMessage(int code) {
        switch (code) {
            case ResponseCode.BAD_REQUEST:
            case ResponseCode.FORBIDDEN:
                return R.string.scan_response_4xx;
            default:
                return R.string.scan_response_5xx;
        }
    }

    @Override
    public void onCheckError(int errorCode, @NonNull String code) {
        Bundle bundle = new Bundle();
        bundle.putString("code", code);
        bundle.putString("error_code", String.valueOf(errorCode));
        analytics.logEvent("error_code", bundle);
        if (errorCode >= 500 && errorCode <= 599) {
            ((BaseActivity)requireActivity()).addFragment(new ErrorServerFragment());
        } else {
            if (requireActivity() instanceof MainActivity1)
                Utils.showTopMessageRed(getCheckErrorMessage(errorCode), ((MainActivity1) requireActivity()));
            else if (requireActivity() instanceof LoginActivity)
                Utils.showTopMessageRed(getCheckErrorMessage(errorCode), ((LoginActivity) requireActivity()));
            hideProgressBar();
            setCursorError(new OnCursorDefaultListener() {
                @Override
                public void onCursorChanged() {
                    resumeScanning();
                }
            });
        }
    }

    @Override
    public void onCheckFailure() {

    }

    @Override
    public void onCheckPbfResponse(@NotNull Response<CheckResponse> response) {
        if (getActivity() instanceof MainActivity1)
            ((MainActivity1) getActivity()).addFragment(getFragmentPdf(response.body()));
        else if (getActivity() instanceof LoginActivity)
            ((LoginActivity) getActivity()).addFragment(getFragmentPdf(response.body()));
    }

    @Override
    public void onResume() {
        super.onResume();
        analyser.resume();
        pbProgress.hide();
    }


}
