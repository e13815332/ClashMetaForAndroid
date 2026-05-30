package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

class LockActivity : AppCompatActivity() {

    companion object {
        private const val AUTH_URL="http://107.173.37.138:54321/verify"
        private const val PREFS_NAME = "zclash_lockscreen"
        private const val KEY_UNLOCKED = "unlocked"

        fun isUnlocked(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNLOCKED, false)
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var unlockButton: Button
    private lateinit var passwordField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 128, 64, 64)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        val title = TextView(this).apply {
            text = "Zclash"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        val hint = TextView(this).apply {
            text = "输入远程验证密码"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(hint)

        passwordField = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#555555"))
            setHint("Password")
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(32, 24, 32, 24)
        }
        layout.addView(passwordField)

        unlockButton = Button(this).apply {
            text = "验证解锁"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0f3460"))
            setPadding(32, 16, 32, 16)
            setOnClickListener { doVerify() }
        }
        layout.addView(unlockButton)

        setContentView(layout)
    }

    private fun doVerify() {
        val pw = passwordField.text.toString()
        if (pw.isEmpty()) return

        unlockButton.isEnabled = false
        unlockButton.text = "验证中..."

        executor.execute {
            try {
                val ok = verifyWithServer(pw)
                handler.post {
                    if (ok) {
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_UNLOCKED, true).apply()
                        val intent = Intent(this@LockActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LockActivity, "密码错误或已过期", Toast.LENGTH_SHORT).show()
                        passwordField.text.clear()
                        unlockButton.isEnabled = true
                        unlockButton.text = "验证解锁"
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this@LockActivity,
                        "网络错误，请检查连接", Toast.LENGTH_SHORT).show()
                    unlockButton.isEnabled = true
                    unlockButton.text = "验证解锁"
                }
            }
        }
    }

    private fun verifyWithServer(password: String): Boolean {
        val conn = URL(AUTH_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        val body = """{"password":"$password"}"""
        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.optBoolean("ok", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
