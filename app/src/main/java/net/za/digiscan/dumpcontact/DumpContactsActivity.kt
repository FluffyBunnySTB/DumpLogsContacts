package net.za.digiscan.dumpcontact

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DumpContactsActivity : AppCompatActivity() {

    private lateinit var exportCallLogButton: Button
    private lateinit var exportSmsButton: Button
    private lateinit var exportContactsButton: Button

    private val allAppPermissionsRequestCode = 101
    private lateinit var requiredPermissions: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_dump_contacts)
        val mainLayout =
            findViewById<ConstraintLayout>(R.id.main_content) // Or whatever your root layout is
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED // Or return the insets if you want to propagate them further
        }
        exportCallLogButton = findViewById(R.id.dumpContactsButton)
        exportSmsButton = findViewById(R.id.exportMessagesButton)
        exportContactsButton = findViewById(R.id.exportContactsButton)

        initializeRequiredPermissions()
        checkAndRequestAllAppPermissions()

        exportCallLogButton.setOnClickListener {
            if (hasPermission(Manifest.permission.READ_CALL_LOG) && hasStoragePermissionIfNeeded()) {
                exportCallLogToCsv()
            } else {
                Toast.makeText(this, "@string/err_call_log_privilege", Toast.LENGTH_SHORT).show()
                checkAndRequestAllAppPermissions()
            }
        }

        exportSmsButton.setOnClickListener {
            if (hasPermission(Manifest.permission.READ_SMS) && hasStoragePermissionIfNeeded()) {
                exportSmsToCsv()
            } else {
                Toast.makeText(this, "@string/err_messages_privilege", Toast.LENGTH_SHORT).show()
                checkAndRequestAllAppPermissions()
            }
        }

        exportContactsButton.setOnClickListener {
            if (hasPermission(Manifest.permission.READ_CONTACTS) && hasStoragePermissionIfNeeded()) {
                exportContactsToCsv()
            } else {
                Toast.makeText(this, "@string/err_contacts_privilege", Toast.LENGTH_SHORT).show()
                checkAndRequestAllAppPermissions()
            }
        }
        updateButtonStates()
    }

    private fun initializeRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requiredPermissions = permissions.toList()
    }

    private fun checkAndRequestAllAppPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), allAppPermissionsRequestCode)
        } else {
            updateButtonStates()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermissionIfNeeded(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            true
        }
    }

    private fun updateButtonStates() {
        exportCallLogButton.isEnabled = hasPermission(Manifest.permission.READ_CALL_LOG) && hasStoragePermissionIfNeeded()
        exportSmsButton.isEnabled = hasPermission(Manifest.permission.READ_SMS) && hasStoragePermissionIfNeeded()
        exportContactsButton.isEnabled = hasPermission(Manifest.permission.READ_CONTACTS) && hasStoragePermissionIfNeeded()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == allAppPermissionsRequestCode) {
            var allGranted = true
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                }
            }

            if (allGranted) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permissions_partially_granted, Toast.LENGTH_LONG).show()
            }
            updateButtonStates()
        }
    }

    private fun exportSmsToCsv() {
        if (!hasPermission(Manifest.permission.READ_SMS) || !hasStoragePermissionIfNeeded()) {
            Toast.makeText(this, "@string/permissions_sms_not_granted", Toast.LENGTH_SHORT).show()
            return
        }

        val smsList = mutableListOf<Map<String, String>>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER
        )

        cursor?.use {
            val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeCol = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val address = it.getString(addressCol)
                var body = it.getString(bodyCol)
                if (body != null) {
                    body = body.replace("\n", "\\\n")
                }
                val dateMillis = it.getLong(dateCol)
                val typeCode = it.getInt(typeCol)

                val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(dateMillis))
                val typeStr = when (typeCode) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
                    Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
                    else -> "Unknown ($typeCode)"
                }

                val entry = mapOf(
                    "Address" to address,
                    "Body" to (body ?: ""),
                    "Date" to formattedDate,
                    "Type" to typeStr
                )
                smsList.add(entry)
            }
        } ?: run {
            Toast.makeText(this, "@string/msg_could_not_read_sms", Toast.LENGTH_SHORT).show()
            return
        }

        if (smsList.isEmpty()) {
            Toast.makeText(this, "@string/msg_no_sms_to_export", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeader = "Date,Address,Type,Body\n"
        val csvData = StringBuilder()
        csvData.append(csvHeader)

        smsList.forEach { row ->
            csvData.append(escapeCsv(row["Date"])).append(",")
            csvData.append(escapeCsv(row["Address"])).append(",")
            csvData.append(escapeCsv(row["Type"])).append(",")
            csvData.append(escapeCsv(row["Body"])).append("\n")
        }

        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = simpleDateFormat.format(Date())
        val dynamicSmsFileName = "sms_export_$currentDateTime.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, dynamicSmsFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, "${downloadsDir.absolutePath}/$dynamicSmsFileName")
                }
            }

            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                @Suppress("DEPRECATION")
                contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }

            uri?.let {
                contentResolver.openOutputStream(it).use { outputStream ->
                    OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write(csvData.toString())
                    }
                }
                Toast.makeText(this, getString(R.string.sms_exported_success_message, dynamicSmsFileName), Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(this, getString(R.string.err_media_store), Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            // Error exporting SMS with message
            Toast.makeText(this, getString(R.string.sms_exported_error_message, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()  //"Error exporting SMS: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            // Unexpected error exporting SMS with message
            Toast.makeText(this, getString(R.string.sms_exported_unexpected_error_message, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun exportContactsToCsv() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS) || !hasStoragePermissionIfNeeded()) {
            Toast.makeText(this, "@string/err_export_contacts_permissions", Toast.LENGTH_SHORT).show()
            return
        }

        val contactsList = mutableListOf<Map<String, String>>()
        val contentResolver = contentResolver

        // Define projection for contacts
        val contactProjection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        // Query contacts
        val contactCursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            contactProjection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )

        contactCursor?.use {
            val idCol = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneCol = it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext()) {
                val contactId = it.getString(idCol)
                val name = it.getString(nameCol) ?: "N/A"
                val hasPhoneNumber = it.getInt(hasPhoneCol) > 0

                val phoneNumbers = mutableListOf<String>()
                if (hasPhoneNumber) {
                    val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        phoneProjection,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )
                    phoneCursor?.use { pCursor ->
                        val numberCol = pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (pCursor.moveToNext()) {
                            phoneNumbers.add(pCursor.getString(numberCol))
                        }
                    }
                }

                val emails = mutableListOf<String>()
                val emailProjection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val emailCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    emailProjection,
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )
                emailCursor?.use { eCursor ->
                    val emailCol = eCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    while (eCursor.moveToNext()) {
                        emails.add(eCursor.getString(emailCol))
                    }
                }

                contactsList.add(mapOf(
                    "Name" to name,
                    "PhoneNumbers" to phoneNumbers.joinToString("; "),
                    "Emails" to emails.joinToString("; ")
                ))
            }
        } ?: run {
            Toast.makeText(this, "@string/err_could_not_read_contacts", Toast.LENGTH_SHORT).show()
            return
        }

        if (contactsList.isEmpty()) {
            Toast.makeText(this, "@strings/err_no_contacts", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeader = "Name,PhoneNumbers,Emails\n"
        val csvData = StringBuilder()
        csvData.append(csvHeader)

        contactsList.forEach { row ->
            csvData.append(escapeCsv(row["Name"])).append(",")
            csvData.append(escapeCsv(row["PhoneNumbers"])).append(",")
            csvData.append(escapeCsv(row["Emails"])).append("\n")
        }

        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = simpleDateFormat.format(Date())
        val dynamicContactsFileName = "contacts_export_$currentDateTime.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, dynamicContactsFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, "${downloadsDir.absolutePath}/$dynamicContactsFileName")
                }
            }

            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                @Suppress("DEPRECATION")
                contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }

            uri?.let {
                contentResolver.openOutputStream(it).use { outputStream ->
                    OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write(csvData.toString())
                    }
                }
                // Contacts export success
                Toast.makeText(this, getString(R.string.contacts_exported_success_message, dynamicContactsFileName), Toast.LENGTH_LONG).show()
            } ?: run {
                // Contact export MediaStore error
                Toast.makeText(this, "@string/contacts_err_creating_media_store", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            // Error exporting Contacts: $message
            Toast.makeText(this, getString(R.string.contacts_err_exporting_contacts, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.contacts_err_unexpected_error, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(data: String?): String {
        if (data == null) {
            return ""
        }
        var escapedData = data.replace("\\\"", "\\\"\\\"")
        if (escapedData.contains(",") || escapedData.contains("\\\"") || escapedData.contains("\n")) {
            escapedData = "\\\"$escapedData\\\""
        }
        return escapedData
    }

    private fun fetchCallLog(): List<Map<String, String>> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            runOnUiThread { Toast.makeText(this, "Cannot fetch call log. Permission missing.", Toast.LENGTH_LONG).show() }
            return emptyList()
        }
        val callLogList = mutableListOf<Map<String, String>>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val typeCode = cursor.getInt(typeCol)
                    val dateMillis = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durationCol)

                    val callTypeStr = when (typeCode) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> "Unknown ($typeCode)"
                    }
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(dateMillis))

                    val entry = mapOf(
                        "Number" to number,
                        "Cached Name" to name,
                        "Type" to callTypeStr,
                        "Date" to formattedDate,
                        "Duration (s)" to duration.toString()
                    )
                    callLogList.add(entry)
                }
            }
        } catch (e: SecurityException) {
            runOnUiThread { Toast.makeText(this, "@string/err_call_log_privilege", Toast.LENGTH_LONG).show() }
            e.printStackTrace()
            return emptyList()
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, getString(R.string.err_reading_call_log, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show() }
            e.printStackTrace()
            return emptyList()
        }
        return callLogList
    }

    private fun exportCallLogToCsv() {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG) || !hasStoragePermissionIfNeeded()) {
            // Cannot export call log. Required permissions missing.
            Toast.makeText(this, "@string/err_call_log_privilege", Toast.LENGTH_SHORT).show()
            return
        }
        val callLogData = fetchCallLog()
        if (callLogData.isEmpty()) {
            // No call log data to export or permission issue.
            Toast.makeText(this, "@string/err_reading_call_log_or_permission_issued", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeader = "Date,Number,Cached Name,Type,Duration (s)\n"
        val csvData = StringBuilder()
        csvData.append(csvHeader)

        callLogData.forEach { row ->
            csvData.append(escapeCsv(row["Date"])).append(",")
            csvData.append(escapeCsv(row["Number"])).append(",")
            csvData.append(escapeCsv(row["Cached Name"])).append(",")
            csvData.append(escapeCsv(row["Type"])).append(",")
            csvData.append(escapeCsv(row["Duration (s)"])).append("\n")
        }

        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = simpleDateFormat.format(Date())
        val dynamicCallLogFileName = "call_log_export_$currentDateTime.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, dynamicCallLogFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, "${downloadsDir.absolutePath}/$dynamicCallLogFileName")
                }
            }
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                @Suppress("DEPRECATION")
                contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }

            uri?.let {
                contentResolver.openOutputStream(it).use { outputStream ->
                    OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write(csvData.toString())
                    }
                }
                Toast.makeText(this, getString(R.string.call_log_success, dynamicCallLogFileName), Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(this, "@string/call_log_error", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.call_log_error_export, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.call_log_error_unexpected, e.message ?: "Unknown Error"), Toast.LENGTH_LONG).show()
        }
    }
}
