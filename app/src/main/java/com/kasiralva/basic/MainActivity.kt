package com.kasiralva.basic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kasiralva.basic.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val db by lazy { KasirAlvaDatabase.get(this) }
    private val licenseManager by lazy { LicenseManager(this) }

    private val createBackupCode = 1001
    private val restoreBackupCode = 1002

    // Menyimpan permission request dari WebView (getUserMedia) sambil menunggu
    // hasil dialog izin kamera Android.
    private var pendingWebPermissionRequest: PermissionRequest? = null

    // File chooser untuk <input type="file"> di WebView (import JSON via HTML)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val req = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (req == null) return@registerForActivityResult
        if (granted) {
            req.grant(req.resources)
        } else {
            req.deny()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            when {
                data.clipData != null && data.clipData!!.itemCount > 0 -> {
                    Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback.onReceiveValue(uris)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (hasCameraPermission()) {
                            request.grant(request.resources)
                        } else {
                            pendingWebPermissionRequest = request
                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Batalkan callback lama kalau ada (user buka file picker berulang)
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    return try {
                        val intent = fileChooserParams?.createIntent()
                            ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                        fileChooserLauncher.launch(intent)
                        true
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        filePathCallback?.onReceiveValue(null)
                        Toast.makeText(
                            this@MainActivity,
                            "Tidak bisa membuka pemilih file: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        false
                    }
                }
            }
            addJavascriptInterface(AppBridge(), "KasirAlvaNative")
        }
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    inner class AppBridge {


        // ------------------------------------------------------------------
        // License (Firestore)
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun licenseStatus(): String {
            return JSONObject().apply {
                put("activated", licenseManager.isActivated())
                put("licenseKey", licenseManager.licenseKey())
                put("deviceHash", licenseManager.deviceHash())
            }.toString()
        }

        @JavascriptInterface
        fun activateLicense(key: String): String {
            // Offline / local fallback only
            return if (licenseManager.activateOffline(key)) "OK" else "INVALID"
        }

        @JavascriptInterface
        fun activateLicenseOnline(key: String): String {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = try {
                    licenseManager.activateOnline(key)
                } catch (_: Exception) {
                    "NETWORK_ERROR"
                }
                runOnUiThread {
                    val safe = result
                        .replace("\\", "")
                        .replace("'", "")
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .take(300)
                    webView.evaluateJavascript(
                        "window.KasirAlvaOnlineActivationResult && window.KasirAlvaOnlineActivationResult('$safe')",
                        null
                    )
                }
            }
            return "PENDING"
        }

        @JavascriptInterface
        fun deactivateLicense(): String {
            licenseManager.deactivateLocal()
            return "OK"
        }

        // ------------------------------------------------------------------
        // Products
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun saveProducts(json: String): String = runBlocking(Dispatchers.IO) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<ProductEntity>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    list += ProductEntity(
                        id = p.optString("id").ifBlank { "p${System.currentTimeMillis()}_$i" },
                        barcode = p.optString("barcode"),
                        name = p.optString("name"),
                        category = p.optString("category"),
                        buyPrice = p.optLong("buyPrice"),
                        sellPrice = p.optLong("sellPrice"),
                        stock = p.optDouble("stock"),
                        minStock = p.optDouble("minStock"),
                        unit = p.optString("unit", "pcs")
                    )
                }
                db.productDao().deleteAll()
                if (list.isNotEmpty()) db.productDao().upsertAll(list)
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun loadProducts(): String = runBlocking(Dispatchers.IO) {
            try {
                val list = db.productDao().getAll()
                JSONArray().also { arr ->
                    list.forEach { p ->
                        arr.put(JSONObject().apply {
                            put("id", p.id)
                            put("barcode", p.barcode)
                            put("name", p.name)
                            put("category", p.category)
                            put("buyPrice", p.buyPrice)
                            put("sellPrice", p.sellPrice)
                            put("stock", p.stock)
                            put("minStock", p.minStock)
                            put("unit", p.unit)
                        })
                    }
                }.toString()
            } catch (_: Exception) {
                "[]"
            }
        }

        // ------------------------------------------------------------------
        // Transactions
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun saveTransactions(json: String): String = runBlocking(Dispatchers.IO) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<TransactionEntity>()
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    // Simpan items + meta (status, returns, profit) dalam satu JSON
                    // agar void/retur tidak hilang saat reload (tanpa migrasi Room).
                    val bundle = JSONObject().apply {
                        put("lines", t.optJSONArray("items") ?: JSONArray())
                        put("status", t.optString("status", "COMPLETED"))
                        put("returns", t.optJSONArray("returns") ?: JSONArray())
                        put("profit", t.optLong("profit"))
                        put("totalBuyPrice", t.optLong("totalBuyPrice"))
                        put("cashierName", t.optString("cashierName", ""))
                    }
                    list += TransactionEntity(
                        id = t.optString("id").ifBlank { "tx_${System.currentTimeMillis()}_$i" },
                        receiptNo = t.optString("receiptNo"),
                        date = t.optString("date"),
                        subtotal = t.optLong("subtotal"),
                        discount = t.optLong("discount"),
                        total = t.optLong("total"),
                        paymentMethod = t.optString("paymentMethod", "CASH"),
                        paid = t.optLong("cashReceived", t.optLong("paid")),
                        changeAmount = t.optLong("change", t.optLong("changeAmount")),
                        itemsJson = bundle.toString(),
                        createdAt = t.optLong("createdAt", System.currentTimeMillis())
                    )
                }
                db.transactionDao().deleteAll()
                if (list.isNotEmpty()) db.transactionDao().upsertAll(list)
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun loadTransactions(): String = runBlocking(Dispatchers.IO) {
            try {
                val list = db.transactionDao().getAll()
                JSONArray().also { arr ->
                    list.forEach { t ->
                        arr.put(JSONObject().apply {
                            put("id", t.id)
                            put("receiptNo", t.receiptNo)
                            put("date", t.date)
                            put("subtotal", t.subtotal)
                            put("discount", t.discount)
                            put("tax", 0)
                            put("total", t.total)
                            put("paymentMethod", t.paymentMethod)
                            put("cashReceived", t.paid)
                            put("paid", t.paid)
                            put("change", t.changeAmount)
                            put("changeAmount", t.changeAmount)
                            put("createdAt", t.createdAt)

                            // Kompatibel: format lama = array items; format baru = {lines, status, returns, ...}
                            val raw = t.itemsJson.trim()
                            if (raw.startsWith("{")) {
                                val bundle = JSONObject(raw)
                                put("items", bundle.optJSONArray("lines") ?: JSONArray())
                                put("status", bundle.optString("status", "COMPLETED"))
                                put("returns", bundle.optJSONArray("returns") ?: JSONArray())
                                put("profit", bundle.optLong("profit"))
                                put("totalBuyPrice", bundle.optLong("totalBuyPrice"))
                                put("cashierName", bundle.optString("cashierName", ""))
                            } else {
                                put("items", JSONArray(if (raw.isBlank()) "[]" else raw))
                                put("status", "COMPLETED")
                                put("returns", JSONArray())
                                put("profit", 0)
                                put("totalBuyPrice", 0)
                            }
                        })
                    }
                }.toString()
            } catch (_: Exception) {
                "[]"
            }
        }

        // ------------------------------------------------------------------
        // Purchases
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun savePurchases(json: String): String = runBlocking(Dispatchers.IO) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<PurchaseEntity>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    list += PurchaseEntity(
                        id = p.optString("id").ifBlank { "pu_${System.currentTimeMillis()}_$i" },
                        invoiceNo = p.optString("invoiceNo"),
                        date = p.optString("date"),
                        supplier = p.optString("supplier"),
                        total = p.optLong("total"),
                        itemsJson = p.optJSONArray("items")?.toString() ?: "[]",
                        createdAt = p.optLong("createdAt", System.currentTimeMillis())
                    )
                }
                db.purchaseDao().deleteAll()
                if (list.isNotEmpty()) db.purchaseDao().upsertAll(list)
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun loadPurchases(): String = runBlocking(Dispatchers.IO) {
            try {
                val list = db.purchaseDao().getAll()
                JSONArray().also { arr ->
                    list.forEach { p ->
                        arr.put(JSONObject().apply {
                            put("id", p.id)
                            put("invoiceNo", p.invoiceNo)
                            put("date", p.date)
                            put("supplier", p.supplier)
                            put("total", p.total)
                            put("items", JSONArray(p.itemsJson))
                            put("createdAt", p.createdAt)
                        })
                    }
                }.toString()
            } catch (_: Exception) {
                "[]"
            }
        }

        // ------------------------------------------------------------------
        // Stock Opname
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun saveStockOpname(json: String): String = runBlocking(Dispatchers.IO) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<StockOpnameEntity>()
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val physical = when {
                        s.has("actualStock") -> s.optDouble("actualStock")
                        else -> s.optDouble("physicalStock")
                    }
                    val system = s.optDouble("systemStock")
                    list += StockOpnameEntity(
                        id = s.optString("id").ifBlank { "op_${System.currentTimeMillis()}_$i" },
                        date = s.optString("date"),
                        productId = s.optString("productId"),
                        productName = s.optString("productName"),
                        systemStock = system,
                        physicalStock = physical,
                        difference = s.optDouble("difference", physical - system),
                        note = s.optString("note"),
                        createdAt = s.optLong("createdAt", System.currentTimeMillis())
                    )
                }
                db.stockOpnameDao().deleteAll()
                if (list.isNotEmpty()) db.stockOpnameDao().upsertAll(list)
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun loadStockOpname(): String = runBlocking(Dispatchers.IO) {
            try {
                val list = db.stockOpnameDao().getAll()
                JSONArray().also { arr ->
                    list.forEach { s ->
                        arr.put(JSONObject().apply {
                            put("id", s.id)
                            put("date", s.date)
                            put("productId", s.productId)
                            put("productName", s.productName)
                            put("systemStock", s.systemStock)
                            put("physicalStock", s.physicalStock)
                            put("actualStock", s.physicalStock)
                            put("difference", s.difference)
                            put("note", s.note)
                            put("createdAt", s.createdAt)
                        })
                    }
                }.toString()
            } catch (_: Exception) {
                "[]"
            }
        }

        // ------------------------------------------------------------------
        // Store Settings
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun saveStoreSettings(json: String): String = runBlocking(Dispatchers.IO) {
            try {
                val s = JSONObject(json)
                db.storeSettingsDao().save(
                    StoreSettingsEntity(
                        id = 1,
                        name = s.optString("name"),
                        address = s.optString("address"),
                        phone = s.optString("phone"),
                        ownerPassword = s.optString("ownerPassword", "1234"),
                        receiptFooter = s.optString("receiptFooter"),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun loadStoreSettings(): String = runBlocking(Dispatchers.IO) {
            try {
                val s = db.storeSettingsDao().get()
                if (s == null) return@runBlocking "{}"
                JSONObject().apply {
                    put("name", s.name)
                    put("address", s.address)
                    put("phone", s.phone)
                    put("ownerPassword", s.ownerPassword)
                    put("receiptFooter", s.receiptFooter)
                }.toString()
            } catch (_: Exception) {
                "{}"
            }
        }

        // ------------------------------------------------------------------
        // Backup / Restore
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun requestBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "kasiralva-backup.json")
                }
                startActivityForResult(intent, createBackupCode)
            }
        }

        @JavascriptInterface
        fun requestRestore() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, restoreBackupCode)
            }
        }

        // ------------------------------------------------------------------
        // Print (struk & laporan) via Android Print Framework.
        // WebView tidak mendukung window.print() bawaan browser, jadi
        // dibungkus lewat PrintManager di sini.
        // ------------------------------------------------------------------

        @JavascriptInterface
        fun printPage(jobName: String) {
            runOnUiThread {
                try {
                    val printManager = getSystemService(PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Perangkat ini tidak mendukung fitur print Android.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@runOnUiThread
                    }
                    val safeName = if (jobName.isBlank()) "KasirAlva Document" else jobName
                    val adapter = webView.createPrintDocumentAdapter(safeName)
                    printManager.print(safeName, adapter, PrintAttributes.Builder().build())
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal membuka print: ${e.javaClass.simpleName} - ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun exportNativeBackup(): String = runBlocking(Dispatchers.IO) {
            JSONObject().apply {
                put("app", "KasirAlva")
                put("version", 5)
                put("exportedAt", System.currentTimeMillis())

                put("products", JSONArray().also { a ->
                    db.productDao().getAll().forEach { p ->
                        a.put(JSONObject().apply {
                            put("id", p.id)
                            put("barcode", p.barcode)
                            put("name", p.name)
                            put("category", p.category)
                            put("buyPrice", p.buyPrice)
                            put("sellPrice", p.sellPrice)
                            put("stock", p.stock)
                            put("minStock", p.minStock)
                            put("unit", p.unit)
                        })
                    }
                })

                put("transactions", JSONArray().also { a ->
                    db.transactionDao().getAll().forEach { t ->
                        a.put(JSONObject().apply {
                            put("id", t.id)
                            put("receiptNo", t.receiptNo)
                            put("date", t.date)
                            put("subtotal", t.subtotal)
                            put("discount", t.discount)
                            put("total", t.total)
                            put("paymentMethod", t.paymentMethod)
                            put("paid", t.paid)
                            put("cashReceived", t.paid)
                            put("change", t.changeAmount)
                            val raw = t.itemsJson.trim()
                            if (raw.startsWith("{")) {
                                val bundle = JSONObject(raw)
                                put("items", bundle.optJSONArray("lines") ?: JSONArray())
                                put("status", bundle.optString("status", "COMPLETED"))
                                put("returns", bundle.optJSONArray("returns") ?: JSONArray())
                                put("profit", bundle.optLong("profit"))
                                put("totalBuyPrice", bundle.optLong("totalBuyPrice"))
                                put("cashierName", bundle.optString("cashierName", ""))
                            } else {
                                put("items", JSONArray(if (raw.isBlank()) "[]" else raw))
                                put("status", "COMPLETED")
                                put("returns", JSONArray())
                            }
                        })
                    }
                })

                put("purchases", JSONArray().also { a ->
                    db.purchaseDao().getAll().forEach { p ->
                        a.put(JSONObject().apply {
                            put("id", p.id)
                            put("invoiceNo", p.invoiceNo)
                            put("date", p.date)
                            put("supplier", p.supplier)
                            put("total", p.total)
                            put("items", JSONArray(p.itemsJson))
                        })
                    }
                })

                put("stockOpname", JSONArray().also { a ->
                    db.stockOpnameDao().getAll().forEach { s ->
                        a.put(JSONObject().apply {
                            put("id", s.id)
                            put("date", s.date)
                            put("productId", s.productId)
                            put("productName", s.productName)
                            put("systemStock", s.systemStock)
                            put("physicalStock", s.physicalStock)
                            put("actualStock", s.physicalStock)
                            put("difference", s.difference)
                            put("note", s.note)
                        })
                    }
                })

                db.storeSettingsDao().get()?.let { s ->
                    put("storeSettings", JSONObject().apply {
                        put("name", s.name)
                        put("address", s.address)
                        put("phone", s.phone)
                        put("ownerPassword", s.ownerPassword)
                        put("receiptFooter", s.receiptFooter)
                    })
                }
            }.toString()
        }

        @JavascriptInterface
        fun restoreNativeBackup(json: String): String {
            return try {
                val root = JSONObject(json)
                if (root.optString("app") != "KasirAlva") return "INVALID_APP"

                runBlocking(Dispatchers.IO) {
                    val products = mutableListOf<ProductEntity>()
                    val pa = root.optJSONArray("products") ?: JSONArray()
                    for (i in 0 until pa.length()) {
                        val p = pa.getJSONObject(i)
                        products += ProductEntity(
                            id = p.optString("id").ifBlank { "p_$i" },
                            barcode = p.optString("barcode"),
                            name = p.optString("name"),
                            category = p.optString("category"),
                            buyPrice = p.optLong("buyPrice"),
                            sellPrice = p.optLong("sellPrice"),
                            stock = p.optDouble("stock"),
                            minStock = p.optDouble("minStock"),
                            unit = p.optString("unit", "pcs")
                        )
                    }

                    val tx = mutableListOf<TransactionEntity>()
                    val ta = root.optJSONArray("transactions") ?: JSONArray()
                    for (i in 0 until ta.length()) {
                        val t = ta.getJSONObject(i)
                        val bundle = JSONObject().apply {
                            put("lines", t.optJSONArray("items") ?: JSONArray())
                            put("status", t.optString("status", "COMPLETED"))
                            put("returns", t.optJSONArray("returns") ?: JSONArray())
                            put("profit", t.optLong("profit"))
                            put("totalBuyPrice", t.optLong("totalBuyPrice"))
                            put("cashierName", t.optString("cashierName", ""))
                        }
                        tx += TransactionEntity(
                            id = t.optString("id"),
                            receiptNo = t.optString("receiptNo"),
                            date = t.optString("date"),
                            subtotal = t.optLong("subtotal"),
                            discount = t.optLong("discount"),
                            total = t.optLong("total"),
                            paymentMethod = t.optString("paymentMethod", "CASH"),
                            paid = t.optLong("paid", t.optLong("cashReceived")),
                            changeAmount = t.optLong("change", t.optLong("changeAmount")),
                            itemsJson = bundle.toString()
                        )
                    }

                    val purchases = mutableListOf<PurchaseEntity>()
                    val pu = root.optJSONArray("purchases") ?: JSONArray()
                    for (i in 0 until pu.length()) {
                        val p = pu.getJSONObject(i)
                        purchases += PurchaseEntity(
                            id = p.optString("id"),
                            invoiceNo = p.optString("invoiceNo"),
                            date = p.optString("date"),
                            supplier = p.optString("supplier"),
                            total = p.optLong("total"),
                            itemsJson = p.optJSONArray("items")?.toString() ?: "[]"
                        )
                    }

                    val opname = mutableListOf<StockOpnameEntity>()
                    val oa = root.optJSONArray("stockOpname")
                        ?: root.optJSONArray("opnames")
                        ?: JSONArray()
                    for (i in 0 until oa.length()) {
                        val s = oa.getJSONObject(i)
                        val physical = when {
                            s.has("actualStock") -> s.optDouble("actualStock")
                            else -> s.optDouble("physicalStock")
                        }
                        val system = s.optDouble("systemStock")
                        opname += StockOpnameEntity(
                            id = s.optString("id"),
                            date = s.optString("date"),
                            productId = s.optString("productId"),
                            productName = s.optString("productName"),
                            systemStock = system,
                            physicalStock = physical,
                            difference = s.optDouble("difference", physical - system),
                            note = s.optString("note")
                        )
                    }

                    db.productDao().deleteAll()
                    db.transactionDao().deleteAll()
                    db.purchaseDao().deleteAll()
                    db.stockOpnameDao().deleteAll()

                    if (products.isNotEmpty()) db.productDao().upsertAll(products)
                    if (tx.isNotEmpty()) db.transactionDao().upsertAll(tx)
                    if (purchases.isNotEmpty()) db.purchaseDao().upsertAll(purchases)
                    if (opname.isNotEmpty()) db.stockOpnameDao().upsertAll(opname)

                    val ss = root.optJSONObject("storeSettings")
                        ?: root.optJSONObject("store")
                    if (ss != null) {
                        db.storeSettingsDao().save(
                            StoreSettingsEntity(
                                id = 1,
                                name = ss.optString("name"),
                                address = ss.optString("address"),
                                phone = ss.optString("phone"),
                                ownerPassword = ss.optString("ownerPassword", "1234"),
                                receiptFooter = ss.optString("receiptFooter")
                            )
                        )
                    }
                }
                "OK"
            } catch (_: Exception) {
                "ERROR"
            }
        }
    }

    @Deprecated("Use Activity Result APIs in future refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (requestCode == createBackupCode) {
                    val json = AppBridge().exportNativeBackup()
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.KasirAlvaBackupResult && window.KasirAlvaBackupResult('backup_ok')",
                            null
                        )
                    }
                } else if (requestCode == restoreBackupCode) {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@launch
                    val result = AppBridge().restoreNativeBackup(json)
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.KasirAlvaBackupResult && window.KasirAlvaBackupResult('$result')",
                            null
                        )
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.KasirAlvaBackupResult && window.KasirAlvaBackupResult('error')",
                        null
                    )
                }
            }
        }
    }
}
