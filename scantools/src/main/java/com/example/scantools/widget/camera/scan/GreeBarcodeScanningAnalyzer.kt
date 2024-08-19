package com.example.scantools.widget.camera.scan

/**
 * @author JoeYe
 * @date 2024/8/13 14:56
 */
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.king.mlkit.vision.common.analyze.CommonAnalyzer

class GreeBarcodeScanningAnalyzer @JvmOverloads constructor(
    barcodeFormat: Int? = null, barcodeFormats: IntArray,
    options: BarcodeScannerOptions? = null, zoomCallback: ZoomSuggestionOptions.ZoomCallback? = null
): CommonAnalyzer<List<Barcode>>() {

    private var realOptions = options ?: if (barcodeFormat != null) {
            BarcodeScannerOptions.Builder()
                .apply {
                    zoomCallback?.let {
                        setZoomSuggestionOptions(
                            ZoomSuggestionOptions
                                .Builder(zoomCallback)
                                .build()
                        )
                    }
                }
                .setBarcodeFormats(barcodeFormat, *barcodeFormats)
                .build()
        } else null

    private var detector: BarcodeScanner = realOptions?.let { BarcodeScanning.getClient(it) } ?: BarcodeScanning.getClient()

    override fun detectInImage(inputImage: InputImage): Task<List<Barcode>> {
        return detector.process(inputImage)
    }
}
