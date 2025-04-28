package com.example.attendanceapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class EmployeeDashboardActivity : AppCompatActivity() {

    private lateinit var empIdInput: EditText
    private lateinit var fromDateBtn: Button
    private lateinit var toDateBtn: Button
    private lateinit var fetchBtn: Button
    private lateinit var summaryText: TextView
    private lateinit var pieChart: PieChart
    private lateinit var broadcastText: TextView
    private lateinit var lastFiveDaysData: TextView

    private var fromDate: String = ""
    private var toDate: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_dashboard)

        empIdInput = findViewById(R.id.empIdInput)
        fromDateBtn = findViewById(R.id.fromDateBtn)
        toDateBtn = findViewById(R.id.toDateBtn)
        fetchBtn = findViewById(R.id.fetchBtn)
        summaryText = findViewById(R.id.summaryText)
        pieChart = findViewById(R.id.pieChart)
        broadcastText = findViewById(R.id.broadcastText)
        lastFiveDaysData = findViewById(R.id.lastFiveDaysData)

        fromDateBtn.setOnClickListener { pickDate(true) }
        toDateBtn.setOnClickListener { pickDate(false) }

        fetchBtn.setOnClickListener {
            val empId = empIdInput.text.toString().trim()
            if (empId.isNotEmpty() && fromDate.isNotEmpty() && toDate.isNotEmpty()) {
                fetchAttendance(empId)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        loadBroadcastMessage()
    }

    private fun pickDate(isFrom: Boolean) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(this, { _, year, month, day ->
            val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
            if (isFrom) {
                fromDate = selectedDate
                fromDateBtn.text = selectedDate
            } else {
                toDate = selectedDate
                toDateBtn.text = selectedDate
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun fetchAttendance(empId: String) {
        db.collection("employeeReports").document(empId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val dailyLog = doc.get("dailyLog") as? Map<*, *> ?: emptyMap<String, String>()
                    val filtered = dailyLog.filterKeys { key ->
                        val dateStr = key.toString()
                        dateStr >= fromDate && dateStr <= toDate
                    }

                    var pl = 0
                    var cl = 0
                    var wo = 0
                    var worked = 0

                    for ((date, rawValue) in filtered) {
                        val value = rawValue?.toString()?.lowercase()?.replace("\"", "")?.trim() ?: continue
                        if (value.contains("pl") || value.contains("cl") || value.contains("wo") || value.contains(":") || value.contains("present")) {
                            when {
                                "pl" in value -> pl++
                                "cl" in value -> cl++
                                "wo" in value -> wo++
                                ":" in value || "present" in value -> worked++
                            }
                        }
                    }

                    val summary = "Worked Days: $worked\nCL: $cl\nPL: $pl\nWeek Offs: $wo"
                    summaryText.text = summary

                    showPieChart(worked, cl, pl, wo)

                    // Prepare Last 5 Records
                    val sortedDailyLog = dailyLog
                        .map { (date, status) -> Pair(date.toString(), status.toString()) }
                        .sortedByDescending { it.first }

                    val lastFiveRecords = sortedDailyLog.take(5)

                    val attendanceSummary = StringBuilder()
                    for ((date, statusRaw) in lastFiveRecords) {
                        val status = statusRaw.lowercase(Locale.getDefault())

                        val displayStatus = when {
                            "cl" in status -> "CL"
                            "pl" in status -> "PL"
                            "wo" in status -> "Week Off"
                            ":" in status || "present" in status -> "Present"
                            else -> "Unknown"
                        }

                        attendanceSummary.append("• $date : $displayStatus\n")
                    }

                    lastFiveDaysData.text = attendanceSummary.toString()

                } else {
                    Toast.makeText(this, "No data found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBroadcastMessage() {
        db.collection("broadcasts").document("latest")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Broadcast error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    broadcastText.text = snapshot.getString("message") ?: ""
                }
            }
    }

    private fun showPieChart(worked: Int, cl: Int, pl: Int, wo: Int) {
        val entries = ArrayList<PieEntry>()
        if (worked > 0) entries.add(PieEntry(worked.toFloat(), "Worked"))
        if (cl > 0) entries.add(PieEntry(cl.toFloat(), "CL"))
        if (pl > 0) entries.add(PieEntry(pl.toFloat(), "PL"))
        if (wo > 0) entries.add(PieEntry(wo.toFloat(), "WO"))

        val dataSet = PieDataSet(entries, "Attendance")
        dataSet.setColors(*ColorTemplate.MATERIAL_COLORS)
        val data = PieData(dataSet)
        data.setValueTextSize(14f)

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.invalidate()
    }
}
