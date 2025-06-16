package net.za.digiscan.dumpcontact

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.CallLog
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DumpContactsActivity : AppCompatActivity() {

    private val requestPermissionsCode = 1
    // Removed: private val callLogFileName = "call_log_export.csv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dump_contacts)

        val exportButton: Button = findViewById(R.id.dumpContactsButton)
        exportButton.text = "Export Call Log" // Ensure button text is correct
        exportButton.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val neededPermissions = mutableListOf<String>()
        neededPermissions.add(Manifest.permission.READ_CALL_LOG)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), requestPermissionsCode)
        } else {
            exportCallLogToCsv()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionsCode) {
            var allPermissionsGranted = true
            // Reconstruct the list of permissions that were needed for this API level
            val currentNeededPermissions = mutableListOf<String>()
            currentNeededPermissions.add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                currentNeededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            for (permission in currentNeededPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                exportCallLogToCsv()
            } else {
                Toast.makeText(this, "Permissions denied. Cannot export call log.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun escapeCsv(data: String?): String {
        if (data == null) {
            return ""
        }
        var escapedData = data.replace("\"", "\"\"") // Rule 1: Replace " with ""
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
            // Rule 2: If it contains comma, quote, or newline, enclose in quotes
            escapedData = "\"$escapedData\""
        }
        return escapedData
    }

    private fun fetchCallLog(): List<Map<String, String>> {
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
            runOnUiThread {
                Toast.makeText(this, "Error reading call log: Permission denied.", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
            return emptyList() // Return empty list on security exception
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error reading call log: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
            return emptyList() // Return empty list on other exceptions
        }
        return callLogList
    }

    private fun exportCallLogToCsv() {
        val callLogData = fetchCallLog()
        if (callLogData.isEmpty()) {
            Toast.makeText(this, "No call log data to export or permission issue.", Toast.LENGTH_SHORT).show()
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

        // Generate filename with date and time
        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = simpleDateFormat.format(Date())
        val dynamicCallLogFileName = "call_log_export_$currentDateTime.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, dynamicCallLogFileName) // Use dynamic filename
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs() // Make sure the directory exists
                    }
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, "${downloadsDir.absolutePath}/$dynamicCallLogFileName") // Use dynamic filename
                }
            }
            var uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }


            uri?.let {
                contentResolver.openOutputStream(it).use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer -> // Use default charset
                        writer.write(csvData.toString())
                    }
                }
                Toast.makeText(this, "Call log exported to Downloads folder as $dynamicCallLogFileName", Toast.LENGTH_LONG).show() // Updated toast
            } ?: run {
                Toast.makeText(this, "Error creating MediaStore entry for CSV.", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error exporting call log: ${e.message}", Toast.LENGTH_LONG).show()
        }  catch (e: Exception) { // Catch any other unexpected errors during file I/O
            e.printStackTrace()
            Toast.makeText(this, "An unexpected error occurred during export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
