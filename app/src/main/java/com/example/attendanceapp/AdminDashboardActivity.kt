package com.example.attendanceapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var selectFileBtn: Button
    private lateinit var broadcastMessageEditText: EditText
    private lateinit var publishBroadcastBtn: Button

    private val PICK_EXCEL_REQUEST = 1
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        selectFileBtn = findViewById(R.id.select_excel_button)
        broadcastMessageEditText = findViewById(R.id.broadcastMessageEditText)
        publishBroadcastBtn = findViewById(R.id.publishBroadcastBtn)

        selectFileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            startActivityForResult(Intent.createChooser(intent, "Select Excel File"), PICK_EXCEL_REQUEST)
        }

        publishBroadcastBtn.setOnClickListener {
            val message = broadcastMessageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                publishBroadcast(message)
            } else {
                Toast.makeText(this, "Please type a broadcast message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_EXCEL_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val fileUri = data.data
            fileUri?.let {
                parseAndUploadToFirestore(it)
            }
        }
    }

    private fun parseAndUploadToFirestore(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            // ✅ Extract the date range from C2
            val reportInfo = sheet.getRow(1)?.getCell(2)?.toString()?.replace("\u00A0", " ")?.trim() ?: ""
            val dateRangeRegex = Regex("""(\d{2}[-/]\d{2}[-/]\d{4})\s*to\s*(\d{2}[-/]\d{2}[-/]\d{4})""", RegexOption.IGNORE_CASE)
            val dateMatch = dateRangeRegex.find(reportInfo)

            if (dateMatch == null) {
                Toast.makeText(this, "❌ Date range not found in cell C2:\n\"$reportInfo\"", Toast.LENGTH_LONG).show()
                return
            }

            val (startDateStr, _) = dateMatch.destructured
            val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val startDate = dateFormatter.parse(startDateStr) ?: Date()

            for (row in sheet) {
                if (row.rowNum < 4) continue // Skip header rows

                val empIdRaw = row.getCell(0)?.toString()?.trim() ?: continue
                val empId = empIdRaw.replace("\n", " ").replace(Regex("[.#\\$\\[\\]/]"), "_").trim()
                if (empId.isBlank()) continue

                val empName = row.getCell(1)?.toString()?.trim() ?: ""
                val ticketNumber = row.getCell(2)?.toString()?.trim() ?: ""

                val dailyData = mutableMapOf<String, String>()
                for (i in 3 until row.lastCellNum) {
                    val cellValue = row.getCell(i)?.toString()?.trim() ?: ""
                    val date = Calendar.getInstance()
                    date.time = startDate
                    date.add(Calendar.DATE, i - 3)
                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
                    dailyData[dateKey] = cellValue
                }

                val empMap = hashMapOf(
                    "name" to empName,
                    "ticketNumber" to ticketNumber,
                    "dailyLog" to dailyData
                )

                db.collection("employeeReports").document(empId)
                    .set(empMap)
                    .addOnFailureListener {
                        Toast.makeText(this, "❌ Failed to upload $empId: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            Toast.makeText(this, "✅ Data uploaded successfully", Toast.LENGTH_LONG).show()
            workbook.close()
            inputStream?.close()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Parsing error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun publishBroadcast(message: String) {
        val broadcastData = hashMapOf(
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("broadcasts").document("latest")
            .set(broadcastData)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Broadcast published", Toast.LENGTH_SHORT).show()
                broadcastMessageEditText.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
