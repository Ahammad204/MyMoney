package com.yourname.mymoney.data

import com.yourname.mymoney.util.DateUtils
import com.yourname.mymoney.util.formatDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupData(
    val transactions: List<TransactionEntity>,
    val loans: List<LoanEntity>,
    val repayments: List<LoanRepaymentEntity>,
    val budgets: List<BudgetEntity>,
    val categories: List<CategoryEntity>,
    val timestamp: Long = 0L,
    val settings: Map<String, String> = emptyMap()
) {
    // Categories are re-seeded on every install, so they never count as "data".
    fun hasRecords(): Boolean =
        transactions.isNotEmpty() ||
            loans.isNotEmpty() ||
            repayments.isNotEmpty() ||
            budgets.isNotEmpty()
}

class BackupValidationException(message: String) : Exception(message)

object DataBackupService {

    const val BACKUP_FORMAT_VERSION = 1

    private val RECURRENCE_VALUES = setOf("NONE", "DAILY", "WEEKLY", "MONTHLY")

    private fun sanitizeRecurrence(raw: String): String {
        val value = raw.trim().uppercase()
        return if (value in RECURRENCE_VALUES) value else "NONE"
    }

    fun exportToCsv(
        transactions: List<TransactionEntity>,
        loansWithDetails: List<LoanWithDetails>,
        budgets: List<CategoryBudgetStatus>
    ): String {
        val sb = StringBuilder()

        // 1. TRANSACTIONS
        sb.append("--- TRANSACTIONS ---\n")
        sb.append("ID,Date,Type,Title,Category,Amount,Recurrence,Note\n")
        transactions.forEach { tx ->
            val dateStr = formatDate(tx.timestamp)
            val cleanTitle = tx.title.replace("\"", "\"\"")
            val cleanNote = tx.note.replace("\"", "\"\"")
            sb.append("${tx.id},\"$dateStr\",${tx.type},\"$cleanTitle\",${tx.category},${tx.amount},${tx.recurrence},\"$cleanNote\"\n")
        }

        sb.append("\n--- LOANS & DEBTS ---\n")
        sb.append("ID,Person,Type,Total Amount,Paid,Remaining,Date,Due Date,Status,Note\n")
        loansWithDetails.forEach { lwd ->
            val loan = lwd.loan
            val dateStr = formatDate(loan.date)
            val dueStr = if (loan.dueDate > 0) formatDate(loan.dueDate) else "None"
            val status = if (lwd.isSettled) "SETTLED" else if (lwd.isOverdue) "OVERDUE" else "ACTIVE"
            val cleanPerson = loan.personName.replace("\"", "\"\"")
            val cleanNote = loan.note.replace("\"", "\"\"")
            sb.append("${loan.id},\"$cleanPerson\",${loan.type},${lwd.totalAmount},${lwd.paidAmount},${lwd.remainingBalance},\"$dateStr\",\"$dueStr\",$status,\"$cleanNote\"\n")
        }

        sb.append("\n--- MONTHLY BUDGETS ---\n")
        sb.append("Category,Monthly Limit,Spent,Status\n")
        budgets.forEach { b ->
            val status = when {
                b.isExceeded -> "EXCEEDED"
                b.isWarning -> "WARNING_80_PERCENT"
                else -> "ON_TRACK"
            }
            sb.append("${b.category},${b.monthlyLimit},${b.spent},$status\n")
        }

        return sb.toString()
    }

    fun exportToJson(
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        repayments: List<LoanRepaymentEntity>,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        settings: Map<String, String> = emptyMap()
    ): String {
        val root = JSONObject()
        root.put("version", BACKUP_FORMAT_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        // Save app preferences, ensuring Gemini API key or credentials are NEVER exported.
        val settingsObj = JSONObject()
        settings.forEach { (key, value) ->
            if (!key.contains("api_key", ignoreCase = true) && !key.contains("gemini", ignoreCase = true)) {
                settingsObj.put(key, value)
            }
        }
        root.put("settings", settingsObj)

        val txArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("title", tx.title)
            obj.put("amount", tx.amount)
            obj.put("type", tx.type)
            obj.put("category", tx.category)
            obj.put("timestamp", tx.timestamp)
            obj.put("note", tx.note)
            obj.put("recurrence", tx.recurrence)
            obj.put("lastGeneratedAt", tx.lastGeneratedAt)
            obj.put("recurrenceActive", tx.recurrenceActive)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val loanArray = JSONArray()
        loans.forEach { l ->
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("personName", l.personName)
            obj.put("amount", l.amount)
            obj.put("type", l.type)
            obj.put("date", l.date)
            obj.put("dueDate", l.dueDate)
            obj.put("note", l.note)
            obj.put("isSettled", l.isSettled)
            obj.put("createdAt", l.createdAt)
            loanArray.put(obj)
        }
        root.put("loans", loanArray)

        val repArray = JSONArray()
        repayments.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("loanId", r.loanId)
            obj.put("amount", r.amount)
            obj.put("date", r.date)
            obj.put("note", r.note)
            repArray.put(obj)
        }
        root.put("repayments", repArray)

        val budgetArray = JSONArray()
        budgets.forEach { b ->
            val obj = JSONObject()
            obj.put("category", b.category)
            obj.put("month", b.month)
            obj.put("monthlyLimit", b.monthlyLimit)
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)

        val catArray = JSONArray()
        categories.forEach { c ->
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("isCustom", c.isCustom)
            catArray.put(obj)
        }
        root.put("categories", catArray)

        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): BackupData {
        val root = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw BackupValidationException("The pasted text is not a valid JSON backup.")
        }
        validateBackup(root)

        val txList = mutableListOf<TransactionEntity>()
        val txArray = root.optJSONArray("transactions")
        if (txArray != null) {
            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                txList.add(
                    TransactionEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.optString("title", "Untitled"),
                        amount = obj.optDouble("amount", 0.0),
                        type = obj.optString("type", "EXPENSE"),
                        category = obj.optString("category", "Other"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", ""),
                        recurrence = sanitizeRecurrence(obj.optString("recurrence", "NONE")),
                        lastGeneratedAt = obj.optLong("lastGeneratedAt", 0L),
                        recurrenceActive = obj.optBoolean("recurrenceActive", true)
                    )
                )
            }
        }

        val loanList = mutableListOf<LoanEntity>()
        val loanArray = root.optJSONArray("loans")
        if (loanArray != null) {
            for (i in 0 until loanArray.length()) {
                val obj = loanArray.getJSONObject(i)
                loanList.add(
                    LoanEntity(
                        id = obj.optLong("id", 0L),
                        personName = obj.optString("personName", "Contact"),
                        amount = obj.optDouble("amount", 0.0),
                        type = obj.optString("type", "LENT"),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        dueDate = obj.optLong("dueDate", 0L),
                        note = obj.optString("note", ""),
                        isSettled = obj.optBoolean("isSettled", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val repList = mutableListOf<LoanRepaymentEntity>()
        val repArray = root.optJSONArray("repayments")
        if (repArray != null) {
            for (i in 0 until repArray.length()) {
                val obj = repArray.getJSONObject(i)
                repList.add(
                    LoanRepaymentEntity(
                        id = obj.optLong("id", 0L),
                        loanId = obj.optLong("loanId", 0L),
                        amount = obj.optDouble("amount", 0.0),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        val budgetList = mutableListOf<BudgetEntity>()
        val budgetArray = root.optJSONArray("budgets")
        if (budgetArray != null) {
            for (i in 0 until budgetArray.length()) {
                val obj = budgetArray.getJSONObject(i)
                budgetList.add(
                    BudgetEntity(
                        category = obj.optString("category", "Other"),
                        month = obj.optString("month", DateUtils.currentMonthKey()),
                        monthlyLimit = obj.optDouble("monthlyLimit", 100.0)
                    )
                )
            }
        }

        val catList = mutableListOf<CategoryEntity>()
        val catArray = root.optJSONArray("categories")
        if (catArray != null) {
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                catList.add(
                    CategoryEntity(
                        name = obj.optString("name", "Other"),
                        isCustom = obj.optBoolean("isCustom", true)
                    )
                )
            }
        }

        val timestamp = root.optLong("timestamp", 0L)
        val settingsMap = mutableMapOf<String, String>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!key.contains("api_key", ignoreCase = true) && !key.contains("gemini", ignoreCase = true)) {
                    settingsMap[key] = settingsObj.optString(key, "")
                }
            }
        }

        return BackupData(txList, loanList, repList, budgetList, catList, timestamp, settingsMap)
    }

    private fun validateBackup(root: JSONObject) {
        val version = readVersion(root)
        if (version > BACKUP_FORMAT_VERSION) {
            throw BackupValidationException(
                "This backup uses format version $version, which was created by a newer version of MyMoney. " +
                    "This app supports version $BACKUP_FORMAT_VERSION."
            )
        }

        val transactions = requireArray(root, "transactions")
        val loans = requireArray(root, "loans")
        val repayments = requireArray(root, "repayments")
        val budgets = requireArray(root, "budgets")
        val categories = requireArray(root, "categories")

        val transactionIds = HashSet<Long>()
        for (i in 0 until transactions.length()) {
            val label = "transactions[$i]"
            val obj = requireObject(transactions, "transactions", i)
            requireString(obj, "title", label)
            requireNumber(obj, "amount", label)
            val type = requireString(obj, "type", label)
            if (type != "INCOME" && type != "EXPENSE") {
                throw BackupValidationException("Backup $label field 'type' must be INCOME or EXPENSE.")
            }
            requireString(obj, "category", label)
            requireTimestamp(obj, "timestamp", label)
            if (obj.has("recurrence")) {
                val recurrence = obj.optString("recurrence", "NONE").uppercase()
                if (recurrence !in RECURRENCE_VALUES) {
                    throw BackupValidationException(
                        "Backup $label field 'recurrence' must be one of NONE, DAILY, WEEKLY, MONTHLY."
                    )
                }
            }
            if (obj.has("id")) {
                val id = requireLong(obj, "id", label)
                if (id > 0L && !transactionIds.add(id)) {
                    throw BackupValidationException("Backup $label has duplicate id $id.")
                }
            }
        }

        val loanIds = HashSet<Long>()
        for (i in 0 until loans.length()) {
            val label = "loans[$i]"
            val obj = requireObject(loans, "loans", i)
            requireString(obj, "personName", label)
            requireNumber(obj, "amount", label)
            val type = requireString(obj, "type", label)
            if (type != "LENT" && type != "BORROWED") {
                throw BackupValidationException("Backup $label field 'type' must be LENT or BORROWED.")
            }
            requireTimestamp(obj, "date", label)
            if (obj.has("id")) {
                val id = requireLong(obj, "id", label)
                if (id > 0L && !loanIds.add(id)) {
                    throw BackupValidationException("Backup $label has duplicate id $id.")
                }
            }
        }

        for (i in 0 until repayments.length()) {
            val label = "repayments[$i]"
            val obj = requireObject(repayments, "repayments", i)
            val loanId = requireLong(obj, "loanId", label)
            if (loanId <= 0L) {
                throw BackupValidationException("Backup $label field 'loanId' must be a positive loan id.")
            }
            if (!loanIds.contains(loanId)) {
                throw BackupValidationException("Backup $label references loan id $loanId, which is missing from 'loans'.")
            }
            requireNumber(obj, "amount", label)
            requireTimestamp(obj, "date", label)
        }

        for (i in 0 until budgets.length()) {
            val label = "budgets[$i]"
            val obj = requireObject(budgets, "budgets", i)
            requireString(obj, "category", label)
            requireNumber(obj, "monthlyLimit", label)
            if (obj.has("month")) {
                val month = requireString(obj, "month", label)
                if (!Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(month)) {
                    throw BackupValidationException("Backup $label field 'month' must look like yyyy-MM.")
                }
            }
        }

        for (i in 0 until categories.length()) {
            val label = "categories[$i]"
            val obj = requireObject(categories, "categories", i)
            requireString(obj, "name", label)
        }
    }

    private fun readVersion(root: JSONObject): Int {
        if (!root.has("version")) {
            throw BackupValidationException("Backup is missing the required 'version' field.")
        }
        val raw = root.opt("version")
        val version = when (raw) {
            is Double -> if (raw % 1.0 == 0.0) raw.toLong() else {
                throw BackupValidationException("Backup field 'version' must be a whole number.")
            }
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
                ?: throw BackupValidationException("Backup field 'version' must be a number.")
            else -> throw BackupValidationException("Backup field 'version' must be a number.")
        }
        if (version < 1L) {
            throw BackupValidationException("Backup field 'version' must be 1 or greater.")
        }
        if (version > Int.MAX_VALUE) {
            throw BackupValidationException("Backup field 'version' is not a valid version number.")
        }
        return version.toInt()
    }

    private fun requireArray(root: JSONObject, key: String): JSONArray {
        if (!root.has(key)) {
            throw BackupValidationException("Backup is missing the required '$key' list.")
        }
        return root.optJSONArray(key)
            ?: throw BackupValidationException("Backup field '$key' must be a list.")
    }

    private fun requireObject(array: JSONArray, key: String, index: Int): JSONObject {
        return array.optJSONObject(index)
            ?: throw BackupValidationException("Backup $key[$index] must be an object.")
    }

    private fun requireString(obj: JSONObject, key: String, label: String): String {
        if (!obj.has(key)) {
            throw BackupValidationException("Backup $label is missing the required '$key' field.")
        }
        val raw = obj.opt(key)
        if (raw !is String) {
            throw BackupValidationException("Backup $label field '$key' must be text.")
        }
        return raw
    }

    private fun requireNumber(obj: JSONObject, key: String, label: String): Double {
        val raw = requireRaw(obj, key, label)
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
                ?: throw BackupValidationException("Backup $label field '$key' must be a number.")
            else -> throw BackupValidationException("Backup $label field '$key' must be a number.")
        }
        if (value.isNaN() || value.isInfinite()) {
            throw BackupValidationException("Backup $label field '$key' must be a number.")
        }
        return value
    }

    private fun requireLong(obj: JSONObject, key: String, label: String): Long {
        val value = requireNumber(obj, key, label)
        if (value % 1.0 != 0.0) {
            throw BackupValidationException("Backup $label field '$key' must be a whole number.")
        }
        return value.toLong()
    }

    private fun requireTimestamp(obj: JSONObject, key: String, label: String): Long {
        val value = requireLong(obj, key, label)
        if (value <= 0L) {
            throw BackupValidationException("Backup $label field '$key' must be a valid timestamp.")
        }
        return value
    }

    private fun requireRaw(obj: JSONObject, key: String, label: String): Any {
        if (!obj.has(key)) {
            throw BackupValidationException("Backup $label is missing the required '$key' field.")
        }
        return obj.opt(key)
    }
}
