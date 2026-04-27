package com.vitbon.kkm.core.fiscal.msposk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над MSPOS-K SDK для фискализации.
 * Реализует FiscalCore через команды MSPOS-K API.
 *
 * SDK поставляется производителем как .aar library.
 * Здесь — обёртка; заменить stub-вызовы на реальные вызовы SDK после интеграции.
 */
@Singleton
class MSPOSKFiscalCore @Inject constructor(
    private val context: Context
) : FiscalCore {

    private val TAG = "MSPOSKFiscalCore"

    // Инициализирован ли SDK
    @Volatile
    private var initialized = false

    // Кэш состояния ФН
    @Volatile
    private var cachedStatus: FiscalStatus? = null

    private val sdk: MSPOSKProtocol by lazy {
        RealMSPOSKProtocol(context)
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing MSPOS-K SDK...")
            // sdk.initialize(context) — вызов SDK производителя
            initialized = true
            Log.d(TAG, "MSPOS-K SDK initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MSPOS-K SDK", e)
            false
        }
    }

    override suspend fun shutdown(): Unit = withContext(Dispatchers.IO) {
        initialized = false
        // sdk.shutdown() — вызов SDK
        Log.d(TAG, "MSPOS-K SDK shut down")
    }

    override suspend fun openShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Opening shift...")
        sdk.openShift()
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing SALE check ${check.id}")
        require(check.type == CheckType.SALE) { "Expected SALE, got ${check.type}" }
        sdk.printSale(check)
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing RETURN check ${check.id}")
        require(check.type == CheckType.RETURN) { "Expected RETURN, got ${check.type}" }
        sdk.printReturn(check)
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing CORRECTION ${doc.id}")
        sdk.printCorrection(doc)
    }

    override suspend fun closeShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Closing shift...")
        cachedStatus = null
        sdk.closeShift()
    }

    override suspend fun printXReport(): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing X-report...")
        sdk.printXReport()
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash in: ${amount.rubles}₽")
        sdk.cashIn(amount, comment)
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash out: ${amount.rubles}₽")
        sdk.cashOut(amount, comment)
    }

    override suspend fun getStatus(): FiscalStatus = withContext(Dispatchers.IO) {
        cachedStatus?.let { return@withContext it }
        sdk.getStatus().also { cachedStatus = it }
    }

    override suspend fun getFFDVersion(): FFDVersion = withContext(Dispatchers.IO) {
        sdk.getFFDVersion()
    }

    /**
     * Оборачивает вызов SDK в обработку ошибок.
     * recoverable = true при transient errors (timeout, busy)
     */
    private suspend fun <T> executeFiscal(block: suspend () -> T): T {
        check(initialized) { "SDK not initialized. Call initialize() first." }
        return try {
            withContext(Dispatchers.IO) {
                block()
            }
        } catch (e: FiscalException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fiscal operation failed", e)
            throw FiscalException(
                errorCode = -1,
                message = e.message ?: "Unknown error",
                recoverable = e is java.util.concurrent.TimeoutException
            )
        }
    }
}

interface MSPOSKProtocol {
    suspend fun openShift(): FiscalResult
    suspend fun printSale(check: FiscalCheck): FiscalResult
    suspend fun printReturn(check: FiscalCheck): FiscalResult
    suspend fun printCorrection(doc: CorrectionDoc): FiscalResult
    suspend fun closeShift(): FiscalResult
    suspend fun printXReport(): FiscalResult
    suspend fun cashIn(amount: Money, comment: String?): FiscalResult
    suspend fun cashOut(amount: Money, comment: String?): FiscalResult
    suspend fun getStatus(): FiscalStatus
    suspend fun getFFDVersion(): FFDVersion
}

class RealMSPOSKProtocol(private val context: Context) : MSPOSKProtocol {

    private companion object {
        private const val SERVICE_PACKAGE = "com.multisoft.drivers.fiscalcore"
        private const val SERVICE_CLASS = "com.multisoft.fiscalcore"
        private const val SERVICE_ACTION = "com.multisoft.drivers.fiscalcore.IFiscalCore"
        private const val BIND_TIMEOUT_MS = 5_000L
    }

    override suspend fun openShift(): FiscalResult = withService("openShift") { svc ->
        val ready = withServiceCall("IsReady") { svc.isReady() }
        if (!ready) throw FiscalException(-1, "MSPOS-K fiscal service is not ready", recoverable = true)
        withServiceCall("OpenDay") { svc.openDay() }
        svc.mapLastError("OpenDay")
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = withService("printSale") { svc ->
        val opened = withServiceCall("OpenRec") { svc.openReceipt(ReceiptType.SALE, check.id) }
        if (!opened) throw FiscalException(-1, "MSPOS-K failed to open SALE receipt")
        check.items.forEach { item ->
            withServiceCall("PrintRecItem") {
                svc.printReceiptItem(
                    name = item.name,
                    quantity = item.quantity,
                    priceKopecks = item.price.kopecks,
                    vatRate = item.vatRate
                )
            }
        }
        check.payments.forEach { payment ->
            withServiceCall("PrintRecItemPay") {
                svc.printPayment(
                    type = payment.type,
                    amountKopecks = payment.amount.kopecks,
                    label = payment.label
                )
            }
        }
        withServiceCall("CloseRec") { svc.closeReceipt() }
        svc.mapLastError("CloseRec")
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = withService("printReturn") { svc ->
        val opened = withServiceCall("OpenRec") { svc.openReceipt(ReceiptType.RETURN, check.id) }
        if (!opened) throw FiscalException(-1, "MSPOS-K failed to open RETURN receipt")
        check.items.forEach { item ->
            withServiceCall("PrintRecItem") {
                svc.printReceiptItem(
                    name = item.name,
                    quantity = item.quantity,
                    priceKopecks = item.price.kopecks,
                    vatRate = item.vatRate
                )
            }
        }
        check.payments.forEach { payment ->
            withServiceCall("PrintRecItemPay") {
                svc.printPayment(
                    type = payment.type,
                    amountKopecks = payment.amount.kopecks,
                    label = payment.label
                )
            }
        }
        withServiceCall("CloseRec") { svc.closeReceipt() }
        svc.mapLastError("CloseRec")
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = withService("printCorrection") { svc ->
        withServiceCall("FNMakeCorrectionRec") {
            svc.makeCorrectionReceipt(
                docType = doc.type,
                baseSumKopecks = doc.baseSum.kopecks,
                cashSumKopecks = doc.cashSum.kopecks,
                cardSumKopecks = doc.cardSum.kopecks,
                reason = doc.reason,
                correctionNumber = doc.correctionNumber,
                correctionDateMs = doc.correctionDate,
                vatRate = doc.vatRate
            )
        }
        svc.mapLastError("FNMakeCorrectionRec")
    }

    override suspend fun closeShift(): FiscalResult = withService("closeShift") { svc ->
        withServiceCall("CloseDay") { svc.closeDay() }
        svc.mapLastError("CloseDay")
    }

    override suspend fun printXReport(): FiscalResult = withService("printXReport") { svc ->
        withServiceCall("PrintXReport") { svc.printXReport() }
        svc.mapLastError("PrintXReport")
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = withService("cashIn") { svc ->
        withServiceCall("CashIn") { svc.cashIn(amount.kopecks, comment) }
        svc.mapLastError("CashIn")
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = withService("cashOut") { svc ->
        withServiceCall("CashOut") { svc.cashOut(amount.kopecks, comment) }
        svc.mapLastError("CashOut")
    }

    override suspend fun getStatus(): FiscalStatus = withService("getStatus") { svc ->
        val status = withServiceCall("GetStatus") { svc.getStatusSnapshot() }
        FiscalStatus(
            fnRegistered = status.fnRegistered,
            fnNumber = status.fnNumber,
            shiftOpen = status.shiftOpen,
            shiftAgeHours = status.shiftAgeHours,
            currentFdNumber = status.currentFdNumber,
            ofdConnected = status.ofdConnected,
            lastError = status.lastError
        )
    }

    override suspend fun getFFDVersion(): FFDVersion = withService("getFFDVersion") { svc ->
        val raw = withServiceCall("GetCurrentFfdVersion") { svc.getCurrentFfdVersion() }
        FFDVersion.fromString(raw)
    }

    private suspend fun <T> withService(operation: String, block: suspend (MSPOSKServiceClient) -> T): T {
        return withContext(Dispatchers.IO) {
            val conn = MSPOSKServiceConnection(context)
            val client = conn.connectOrThrow(operation)
            try {
                block(client)
            } finally {
                conn.disconnectSafely()
            }
        }
    }

    private suspend fun <T> withServiceCall(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: TimeoutCancellationException) {
            throw FiscalException(-1, "MSPOS-K $operation timeout", recoverable = true)
        } catch (e: FiscalException) {
            throw e
        } catch (e: Exception) {
            throw FiscalException(-1, "MSPOS-K $operation failed: ${e.message}", recoverable = false)
        }
    }

    private class MSPOSKServiceConnection(private val context: Context) {
        private var isBound = false
        private val serviceDeferred = CompletableDeferred<MSPOSKServiceClient>()

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    serviceDeferred.completeExceptionally(FiscalException(-1, "MSPOS-K service binder is null"))
                    return
                }
                serviceDeferred.complete(BinderBackedMSPOSKServiceClient(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!serviceDeferred.isCompleted) {
                    serviceDeferred.completeExceptionally(FiscalException(-1, "MSPOS-K service disconnected"))
                }
            }
        }

        suspend fun connectOrThrow(operation: String): MSPOSKServiceClient {
            val intent = Intent(SERVICE_ACTION).apply {
                setClassName(SERVICE_PACKAGE, SERVICE_CLASS)
            }
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                throw FiscalException(-1, "MSPOS-K fiscal service not available for $operation", recoverable = true)
            }
            isBound = true
            return try {
                withTimeout(BIND_TIMEOUT_MS) { serviceDeferred.await() }
            } catch (e: TimeoutCancellationException) {
                throw FiscalException(-1, "MSPOS-K bind timeout for $operation", recoverable = true)
            }
        }

        fun disconnectSafely() {
            if (!isBound) return
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            } finally {
                isBound = false
            }
        }
    }

    private interface MSPOSKServiceClient {
        suspend fun isReady(): Boolean
        suspend fun openDay()
        suspend fun openReceipt(type: ReceiptType, documentId: String): Boolean
        suspend fun printReceiptItem(name: String, quantity: Double, priceKopecks: Long, vatRate: VatRate)
        suspend fun printPayment(type: PaymentType, amountKopecks: Long, label: String)
        suspend fun closeReceipt()
        suspend fun makeCorrectionReceipt(
            docType: CheckType,
            baseSumKopecks: Long,
            cashSumKopecks: Long,
            cardSumKopecks: Long,
            reason: String,
            correctionNumber: String,
            correctionDateMs: Long,
            vatRate: VatRate
        )
        suspend fun closeDay()
        suspend fun printXReport()
        suspend fun cashIn(amountKopecks: Long, comment: String?)
        suspend fun cashOut(amountKopecks: Long, comment: String?)
        suspend fun getStatusSnapshot(): ServiceStatusSnapshot
        suspend fun getCurrentFfdVersion(): String

        suspend fun mapLastError(operation: String): FiscalResult
    }

    private class BinderBackedMSPOSKServiceClient(private val binder: IBinder) : MSPOSKServiceClient {
        private companion object {
            private const val IFISCAL_CORE_STUB = "com.multisoft.drivers.fiscalcore.IFiscalCore\$Stub"
            private const val CALLBACK_STUB = "com.multisoft.drivers.fiscalcore.IExceptionCallback\$Stub"
            private const val ERR_CODE_WRONG_STATUS = 3
            private const val EXT_STATUS_NOT_ENOUGH_CASH = 7
            private const val EXT_STATUS_SERVICE_NOT_READY = 0
            private const val EXT_STATUS_MARKING_RETRY = 21
            private const val EXT_STATUS_KEYS_UPDATE_ERROR = 19
        }

        private val core = resolveCoreInterface(binder)
        private val callbackStubClass = loadClass(CALLBACK_STUB)
        private val intArrayClass = IntArray::class.java
        private val stringArrayClass = Array<String>::class.java
        private val anyListClass = java.util.List::class.java

        private val coreMethods = mutableMapOf<String, Method>()

        private data class CallbackError(
            val errCode: Int,
            val message: String,
            val extErrCode: Int,
            val stack: String
        )

        private data class CallbackState(
            @Volatile var error: CallbackError? = null
        )

        override suspend fun isReady(): Boolean {
            return (tryInvoke("IsReady") as? Boolean) ?: false
        }

        override suspend fun openDay() {
            invokeVoidWithCallback("OpenDay", defaultCashierName())
        }

        override suspend fun openReceipt(type: ReceiptType, documentId: String): Boolean {
            invokeVoidWithCallback("OpenRec", type.code)
            return true
        }

        override suspend fun printReceiptItem(name: String, quantity: Double, priceKopecks: Long, vatRate: VatRate) {
            val count = quantity.toPlainDecimal(3)
            val price = priceKopecks.toMoneyString()
            val itemName = name.ifBlank { "Товар" }
            val article = itemName
            invokeVoidWithCallback("PrintRecItem", count, price, itemName, article)

            val taxNum = vatRate.toTaxNumCode()
            invokeVoidWithCallback("SetSumTaxes", intArrayOf(taxNum), arrayOf(price))
        }

        override suspend fun printPayment(type: PaymentType, amountKopecks: Long, label: String) {
            invokeVoidWithCallback(
                "PrintRecItemPay",
                type.toPayTypeCode(),
                amountKopecks.toMoneyString(),
                label
            )
        }

        override suspend fun closeReceipt() {
            invokeVoidWithCallback("CloseRec")
        }

        override suspend fun makeCorrectionReceipt(
            docType: CheckType,
            baseSumKopecks: Long,
            cashSumKopecks: Long,
            cardSumKopecks: Long,
            reason: String,
            correctionNumber: String,
            correctionDateMs: Long,
            vatRate: VatRate
        ) {
            val recType = docType.toCorrectionRecTypeCode()
            val taxNum = vatRate.toTaxNumCode()
            val docDateIso = correctionDateMs.toVendorDateTimeIso()
            val cash = cashSumKopecks.toMoneyString()
            val card = cardSumKopecks.toMoneyString()
            val cashAndCard = cashSumKopecks + cardSumKopecks
            val other = when {
                baseSumKopecks > cashAndCard -> (baseSumKopecks - cashAndCard).toMoneyString()
                else -> "0.00"
            }

            invokeVoidWithCallback(
                "FNMakeCorrectionRec",
                recType,
                cash,
                card,
                "0.00",
                "0.00",
                other,
                taxNum,
                0,
                reason,
                docDateIso,
                correctionNumber
            )
        }

        override suspend fun closeDay() {
            invokeVoidWithCallback("CloseDay", defaultCashierName())
        }

        override suspend fun printXReport() {
            invokeVoidWithCallback("PrintXReport")
        }

        override suspend fun cashIn(amountKopecks: Long, comment: String?) {
            invokeVoidWithCallback("OpenRec", ReceiptType.CASH_IN.code)
            invokeVoidWithCallback("PrintRecItemPay", PaymentType.CASH.toPayTypeCode(), amountKopecks.toMoneyString(), comment.orEmpty())
            invokeVoidWithCallback("CloseRec")
        }

        override suspend fun cashOut(amountKopecks: Long, comment: String?) {
            invokeVoidWithCallback("OpenRec", ReceiptType.CASH_OUT.code)
            invokeVoidWithCallback("PrintRecItemPay", PaymentType.CASH.toPayTypeCode(), amountKopecks.toMoneyString(), comment.orEmpty())
            invokeVoidWithCallback("CloseRec")
        }

        override suspend fun getStatusSnapshot(): ServiceStatusSnapshot {
            val fnNumber = tryStringWithCallback("FNGetNumber")
            val fnState = tryIntWithCallback("FNGetState")
            val dayState = tryIntWithCallback("GetDayState")
            val fdNumber = tryLongWithCallback("FNGetLastFDNum") ?: tryLongWithCallback("FNGetLastFDNumber")
            val ofdState = tryIntWithCallback("OFDGetConnectionStatus")
            val warningFlags = tryIntWithCallback("FNGetWarningFlags")
            val notificationStatus = tryIntWithCallback("NotificationStatusOut_GetStatus")

            val fnRegistered = fnNumber != null && fnState != null && fnState > 0
            val shiftOpen = dayState == 1
            val currentFd = (fdNumber ?: 0L).toInt().coerceAtLeast(0)
            val ofdConnected = ofdState == 1
            val warning = when {
                warningFlags != null && warningFlags != 0 -> "FN warning flags: $warningFlags"
                notificationStatus != null && notificationStatus != 0 -> "Notification status: $notificationStatus"
                else -> null
            }

            return ServiceStatusSnapshot(
                fnRegistered = fnRegistered,
                fnNumber = fnNumber,
                shiftOpen = shiftOpen,
                shiftAgeHours = null,
                currentFdNumber = currentFd,
                ofdConnected = ofdConnected,
                lastError = warning
            )
        }

        override suspend fun getCurrentFfdVersion(): String {
            val raw = tryIntWithCallback("GetCurrentFfdVersion") ?: tryIntWithCallback("FNGetFnFfdVersion") ?: 2
            return when (raw) {
                4, 3 -> "1.2"
                2 -> "1.05"
                else -> "1.05"
            }
        }

        override suspend fun mapLastError(operation: String): FiscalResult {
            val fiscalSign = tryLongWithCallback("FDI_GetFiscalSign")?.takeIf { it > 0L }?.toString()
                ?: tryStringWithCallback("OfdOut_GetFiscalSign")?.takeIf { it.isNotBlank() && it != "0" }
            val fnNumber = tryStringWithCallback("FNGetNumber")?.takeIf { it.isNotBlank() }
            val fdNumber = tryLongWithCallback("FNGetLastFDNum")?.takeIf { it > 0L }?.toString()
                ?: tryLongWithCallback("FNGetLastFDNumber")?.takeIf { it > 0L }?.toString()

            if (fiscalSign == null || fnNumber == null || fdNumber == null) {
                throw FiscalException(
                    errorCode = -1,
                    message = "MSPOS-K $operation finished without valid fiscal identifiers",
                    recoverable = true
                )
            }

            val ts = System.currentTimeMillis()
            return FiscalResult.Success(
                fiscalSign = fiscalSign,
                fnNumber = fnNumber,
                fdNumber = fdNumber,
                timestamp = ts
            )
        }

        private fun invokeVoidWithCallback(name: String, vararg args: Any?) {
            val state = CallbackState()
            val callback = createExceptionCallbackProxy(state)
            invokeByName(name, *args, callback)
            state.error?.let { throw mapVendorError(name, it) }
        }

        private fun tryIntWithCallback(name: String): Int? {
            return runCatching {
                val state = CallbackState()
                val callback = createExceptionCallbackProxy(state)
                val result = invokeByName(name, callback)
                if (state.error != null) return null
                (result as? Number)?.toInt()
            }.getOrNull()
        }

        private fun tryLongWithCallback(name: String): Long? {
            return runCatching {
                val state = CallbackState()
                val callback = createExceptionCallbackProxy(state)
                val result = invokeByName(name, callback)
                if (state.error != null) return null
                (result as? Number)?.toLong()
            }.getOrNull()
        }

        private fun tryStringWithCallback(name: String): String? {
            return runCatching {
                val state = CallbackState()
                val callback = createExceptionCallbackProxy(state)
                val result = invokeByName(name, callback)
                if (state.error != null) return null
                result as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        private fun invokeByName(name: String, vararg args: Any?): Any? {
            val method = resolveMethod(name, args.toList())
            return try {
                method.invoke(core, *args)
            } catch (e: InvocationTargetException) {
                val cause = e.targetException ?: e
                throw FiscalException(-1, "MSPOS-K $name invocation failed: ${cause.message}", recoverable = false)
            } catch (e: Exception) {
                throw FiscalException(-1, "MSPOS-K $name invocation failed: ${e.message}", recoverable = false)
            }
        }

        private fun tryInvoke(name: String, vararg args: Any?): Any? {
            return try {
                invokeByName(name, *args)
            } catch (_: FiscalException) {
                null
            }
        }

        private fun resolveMethod(name: String, args: List<Any?>): Method {
            coreMethods[methodKey(name, args)]?.let { return it }
            val candidates = core.javaClass.methods.filter { it.name == name }
            val found = candidates.firstOrNull { matches(it.parameterTypes, args) }
                ?: throw FiscalException(-1, "MSPOS-K API method $name with matching signature not found", recoverable = false)
            coreMethods[methodKey(name, args)] = found
            return found
        }

        private fun methodKey(name: String, args: List<Any?>): String {
            val sig = args.joinToString(",") { arg ->
                arg?.javaClass?.name ?: "null"
            }
            return "$name|$sig"
        }

        private fun matches(paramTypes: Array<Class<*>>, args: List<Any?>): Boolean {
            if (paramTypes.size != args.size) return false
            return paramTypes.zip(args).all { (param, arg) ->
                when {
                    arg == null -> !param.isPrimitive
                    param == java.lang.Integer.TYPE -> arg is Int
                    param == java.lang.Long.TYPE -> arg is Long
                    param == java.lang.Boolean.TYPE -> arg is Boolean
                    param == java.lang.Double.TYPE -> arg is Double
                    param == java.lang.Float.TYPE -> arg is Float
                    param == java.lang.Short.TYPE -> arg is Short
                    param == java.lang.Byte.TYPE -> arg is Byte
                    param == java.lang.Character.TYPE -> arg is Char
                    param == intArrayClass -> arg is IntArray
                    param == stringArrayClass -> arg is Array<*>
                    anyListClass.isAssignableFrom(param) -> arg is List<*>
                    else -> param.isAssignableFrom(arg.javaClass)
                }
            }
        }

        private fun createExceptionCallbackProxy(state: CallbackState): Any {
            val callbackIntf = callbackStubClass.interfaces.firstOrNull()
                ?: throw FiscalException(-1, "MSPOS-K callback interface not found", recoverable = false)

            val callbackDescriptor = callbackIntf.name
            val transactionHandleException = IBinder.FIRST_CALL_TRANSACTION

            val binderWrapper = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == IBinder.INTERFACE_TRANSACTION) {
                        reply?.writeString(callbackDescriptor)
                        return true
                    }
                    if (code == transactionHandleException) {
                        data.enforceInterface(callbackDescriptor)
                        val errCode = data.readInt()
                        val message = data.readString().orEmpty()
                        val extErrCode = data.readInt()
                        val stack = data.readString().orEmpty()
                        state.error = CallbackError(
                            errCode = errCode,
                            message = message,
                            extErrCode = extErrCode,
                            stack = stack
                        )
                        reply?.writeNoException()
                        return true
                    }
                    return super.onTransact(code, data, reply, flags)
                }
            }

            return try {
                val asInterface = callbackStubClass.getMethod("asInterface", IBinder::class.java)
                asInterface.invoke(null, binderWrapper)
                    ?: throw FiscalException(-1, "MSPOS-K callback asInterface returned null", recoverable = false)
            } catch (e: InvocationTargetException) {
                val cause = e.targetException ?: e
                throw FiscalException(-1, "MSPOS-K callback bridge init failed: ${cause.message}", recoverable = false)
            } catch (e: Exception) {
                throw FiscalException(-1, "MSPOS-K callback bridge init failed: ${e.message}", recoverable = false)
            }
        }

        private fun mapVendorError(operation: String, err: CallbackError): FiscalException {
            val message = err.message.ifBlank { "Vendor fiscal operation failed" }
            val recoverable = when {
                err.errCode == ERR_CODE_WRONG_STATUS && err.extErrCode == EXT_STATUS_SERVICE_NOT_READY -> true
                err.errCode == ERR_CODE_WRONG_STATUS && err.extErrCode == EXT_STATUS_MARKING_RETRY -> true
                err.errCode == ERR_CODE_WRONG_STATUS && err.extErrCode == EXT_STATUS_KEYS_UPDATE_ERROR -> true
                err.errCode == ERR_CODE_WRONG_STATUS && err.extErrCode == EXT_STATUS_NOT_ENOUGH_CASH -> false
                else -> false
            }
            return FiscalException(
                errorCode = err.errCode,
                message = "MSPOS-K $operation failed: $message (ext=${err.extErrCode})",
                recoverable = recoverable
            )
        }

        private fun resolveCoreInterface(serviceBinder: IBinder): Any {
            val stubClass = loadClass(IFISCAL_CORE_STUB)
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            return try {
                asInterface.invoke(null, serviceBinder)
                    ?: throw FiscalException(-1, "MSPOS-K asInterface returned null", recoverable = true)
            } catch (e: InvocationTargetException) {
                val cause = e.targetException ?: e
                throw FiscalException(-1, "MSPOS-K binder bridge init failed: ${cause.message}", recoverable = true)
            } catch (e: Exception) {
                throw FiscalException(-1, "MSPOS-K binder bridge init failed: ${e.message}", recoverable = true)
            }
        }

        private fun loadClass(name: String): Class<*> {
            return try {
                Class.forName(name)
            } catch (e: ClassNotFoundException) {
                throw FiscalException(
                    errorCode = -1,
                    message = "MSPOS-K required class not found: $name",
                    recoverable = true
                )
            }
        }

        private fun defaultValue(returnType: Class<*>): Any? = when {
            !returnType.isPrimitive -> null
            returnType == java.lang.Boolean.TYPE -> false
            returnType == java.lang.Integer.TYPE -> 0
            returnType == java.lang.Long.TYPE -> 0L
            returnType == java.lang.Double.TYPE -> 0.0
            returnType == java.lang.Float.TYPE -> 0f
            returnType == java.lang.Short.TYPE -> 0.toShort()
            returnType == java.lang.Byte.TYPE -> 0.toByte()
            returnType == java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }

        private fun defaultCashierName(): String = "Кассир"

        private fun Double.toPlainDecimal(scale: Int): String {
            return BigDecimal.valueOf(this).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }

        private fun Long.toMoneyString(): String {
            return BigDecimal.valueOf(this, 2).setScale(2, RoundingMode.HALF_UP).toPlainString()
        }

        private fun Long.toVendorDateTimeIso(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date(this))
        }

        private fun ReceiptType.toPayTypeCode(): Int = when (this) {
            ReceiptType.SALE,
            ReceiptType.RETURN,
            ReceiptType.CORRECTION_SALE,
            ReceiptType.CORRECTION_RETURN,
            ReceiptType.CASH_IN,
            ReceiptType.CASH_OUT -> PaymentType.CASH.toPayTypeCode()
        }

        private fun PaymentType.toPayTypeCode(): Int = when (this) {
            PaymentType.CASH -> 0
            PaymentType.CARD,
            PaymentType.SBP,
            PaymentType.BONUS,
            PaymentType.MIXED -> 1
        }

        private fun VatRate.toTaxNumCode(): Int = when (this) {
            VatRate.VAT_22 -> 10
            VatRate.VAT_10 -> 1
            VatRate.VAT_0 -> 4
            VatRate.VAT_5 -> 8
            VatRate.VAT_7 -> 9
            VatRate.NO_VAT -> 5
        }

        private fun CheckType.toCorrectionRecTypeCode(): Int = when (this) {
            CheckType.CORRECTION_INCOME -> 0
            CheckType.CORRECTION_EXPENSE -> 1
            else -> 0
        }
    }

    private enum class ReceiptType(val code: Int) {
        SALE(1),
        RETURN(3),
        CASH_IN(7),
        CASH_OUT(8),
        CORRECTION_SALE(21),
        CORRECTION_RETURN(23)
    }

    private data class ServiceStatusSnapshot(
        val fnRegistered: Boolean,
        val fnNumber: String?,
        val shiftOpen: Boolean,
        val shiftAgeHours: Long?,
        val currentFdNumber: Int,
        val ofdConnected: Boolean,
        val lastError: String?
    )
}
