package com.example.mmdviewer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnLoadModel: Button

    // Toggles & Inputs
    private lateinit var cbSelfShadow: CheckBox
    private lateinit var cbGroundShadow: CheckBox
    private lateinit var cbAxes: CheckBox
    private lateinit var etModelPosX: EditText
    private lateinit var etModelPosY: EditText
    private lateinit var etModelPosZ: EditText
    private lateinit var etModelRotX: EditText
    private lateinit var etModelRotY: EditText
    private lateinit var etModelRotZ: EditText
    private lateinit var btnApplyModel: Button
    private lateinit var etCamPosX: EditText
    private lateinit var etCamPosY: EditText
    private lateinit var etCamPosZ: EditText
    private lateinit var etCamRotX: EditText
    private lateinit var etCamRotY: EditText
    private lateinit var etCamRotZ: EditText
    private lateinit var btnApplyCamera: Button

    // The Folder Picker!
    // The Folder Picker!
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Let the user know it might take a moment
            webView.evaluateJavascript("logMsg('Copying folder... this may take 15-30 seconds for large models!');", null)

            // Run folder copying in the background so the app doesn't freeze
            Thread {
                try {
                    val destDir = File(filesDir, "current_model")
                    destDir.deleteRecursively() // Delete the old model to save space
                    destDir.mkdirs()

                    val rootDoc = DocumentFile.fromTreeUri(this, uri)
                    if (rootDoc != null) {
                        copyDocumentFile(rootDoc, destDir)

                        // Search the folder we just copied for a .pmx or .pmd file
                        val modelFile = findModelFile(destDir)

                        if (modelFile != null) {
                            // Create a secure local URL that WebViewAssetLoader can intercept
                            val relativePath = modelFile.absolutePath.substringAfter(filesDir.absolutePath)
                            val localUrl = "https://appassets.androidplatform.net/local$relativePath"

                            runOnUiThread {
                                webView.evaluateJavascript("loadLocalModel('$localUrl');", null)
                            }
                        } else {
                            runOnUiThread {
                                webView.evaluateJavascript("logMsg('<span style=\"color:orange\">No .pmx or .pmd found in folder!</span>');", null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // IF IT CRASHES, WE WILL NOW SEE IT ON SCREEN!
                    runOnUiThread {
                        webView.evaluateJavascript("logMsg('<span style=\"color:red\">Copy Error: ${e.message}</span>');", null)
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webView)
        btnLoadModel = findViewById(R.id.btnLoadModel)

        cbSelfShadow = findViewById(R.id.cbSelfShadow)
        cbGroundShadow = findViewById(R.id.cbGroundShadow)
        cbAxes = findViewById(R.id.cbAxes)
        etModelPosX = findViewById(R.id.etModelPosX)
        etModelPosY = findViewById(R.id.etModelPosY)
        etModelPosZ = findViewById(R.id.etModelPosZ)
        etModelRotX = findViewById(R.id.etModelRotX)
        etModelRotY = findViewById(R.id.etModelRotY)
        etModelRotZ = findViewById(R.id.etModelRotZ)
        btnApplyModel = findViewById(R.id.btnApplyModel)
        etCamPosX = findViewById(R.id.etCamPosX)
        etCamPosY = findViewById(R.id.etCamPosY)
        etCamPosZ = findViewById(R.id.etCamPosZ)
        etCamRotX = findViewById(R.id.etCamRotX)
        etCamRotY = findViewById(R.id.etCamRotY)
        etCamRotZ = findViewById(R.id.etCamRotZ)
        btnApplyCamera = findViewById(R.id.btnApplyCamera)

        setupWebView()

        btnLoadModel.setOnClickListener {
            folderPicker.launch(null) // Opens Android Folder Picker
        }

        // Setup Listeners
        cbSelfShadow.setOnCheckedChangeListener { _, isChecked -> webView.evaluateJavascript("setSelfShadow($isChecked);", null) }
        cbGroundShadow.setOnCheckedChangeListener { _, isChecked -> webView.evaluateJavascript("setGroundShadow($isChecked);", null) }
        cbAxes.setOnCheckedChangeListener { _, isChecked -> webView.evaluateJavascript("setAxesAndPlanes($isChecked);", null) }

        btnApplyModel.setOnClickListener {
            val js = "setModelTransform(${getDecimal(etModelPosX)}, ${getDecimal(etModelPosY)}, ${getDecimal(etModelPosZ)}, ${getDecimal(etModelRotX)}, ${getDecimal(etModelRotY)}, ${getDecimal(etModelRotZ)});"
            webView.evaluateJavascript(js, null)
        }

        btnApplyCamera.setOnClickListener {
            val js = "setCameraTransform(${getDecimal(etCamPosX)}, ${getDecimal(etCamPosY)}, ${getDecimal(etCamPosZ)}, ${getDecimal(etCamRotX)}, ${getDecimal(etCamRotY)}, ${getDecimal(etCamRotZ)});"
            webView.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // The Magic Local Server! This allows WebGL to load images securely without CORS errors.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/local/", WebViewAssetLoader.InternalStoragePathHandler(this, filesDir))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("MMD_WEBVIEW", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }

        // We now load the HTML file through our local server domain instead of file:///
        webView.loadUrl("https://appassets.androidplatform.net/assets/mmd.html")
    }

    private fun getDecimal(editText: EditText): Float = editText.text.toString().toFloatOrNull() ?: 0.0f

    // --- FOLDER COPYING UTILITIES ---

    private fun copyDocumentFile(docFile: DocumentFile, destDir: File) {
        if (docFile.isDirectory) {
            val newDir = File(destDir, docFile.name ?: "folder")
            newDir.mkdirs()
            docFile.listFiles().forEach { copyDocumentFile(it, newDir) }
        } else {
            val destFile = File(destDir, docFile.name ?: "file")
            contentResolver.openInputStream(docFile.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun findModelFile(dir: File): File? {
        val files = dir.listFiles() ?: return null
        // Look for pmx or pmd
        files.forEach {
            if (it.isFile && (it.name.endsWith(".pmx", true) || it.name.endsWith(".pmd", true))) {
                return it
            }
        }
        // Search inside subfolders (some models are nested)
        files.forEach {
            if (it.isDirectory) {
                val found = findModelFile(it)
                if (found != null) return found
            }
        }
        return null
    }

    // --- LIFECYCLE MANAGEMENT ---
    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}