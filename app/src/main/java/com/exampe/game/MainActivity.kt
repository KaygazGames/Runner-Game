package com.example.game

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.ImageView
class MainActivity : AppCompatActivity() {

private lateinit var gameView: GameView
private lateinit var scoreText: TextView
private lateinit var highScoreText: TextView
private lateinit var gameOverLayout: LinearLayout
private lateinit var restartButton: Button
private lateinit var reviveButton: Button
private lateinit var crouchButton: Button
private lateinit var pauseButton: Button
private lateinit var adLayout: LinearLayout
private lateinit var resumeAfterAd: Button
private lateinit var skinButton: Button

private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
setContentView(R.layout.activity_main)

// find views
gameView = findViewById(R.id.gameView)
scoreText = findViewById(R.id.scoreText)
highScoreText = findViewById(R.id.highScoreText)
gameOverLayout = findViewById(R.id.gameOverLayout)
restartButton = findViewById(R.id.restartButton)
reviveButton = findViewById(R.id.reviveButton)
crouchButton = findViewById(R.id.crouchButton)
pauseButton = findViewById(R.id.pauseButton)
adLayout = findViewById(R.id.adLayout)
resumeAfterAd = findViewById(R.id.resumeAfterAd)
skinButton = findViewById(R.id.skinButton)

// UI update
uiHandler.post(object : Runnable {
override fun run() {
scoreText.text = "Skor: ${gameView.score}"
highScoreText.text = "Rekor: ${gameView.highScore}"
gameOverLayout.visibility = if (gameView.isGameOver) LinearLayout.VISIBLE else LinearLayout.GONE
uiHandler.postDelayed(this, 100)
}
})

restartButton.setOnClickListener {
gameOverLayout.visibility = LinearLayout.GONE
gameView.resetGame()
}

reviveButton.setOnClickListener {
gameOverLayout.visibility = LinearLayout.GONE
gameView.revivePlayer()
}

crouchButton.setOnTouchListener { _, event ->
when (event.action) {
android.view.MotionEvent.ACTION_DOWN -> gameView.crouchDown()
android.view.MotionEvent.ACTION_UP -> gameView.crouchUp()
}
true
}

pauseButton.setOnClickListener {
if (gameView.isPaused) {
gameView.resumeGame()
pauseButton.text = "Durdur"
} else {
gameView.pauseGame()
pauseButton.text = "Devam Et"
}
}

resumeAfterAd.setOnClickListener {
adLayout.visibility = LinearLayout.GONE
gameView.resumeGame()
}

// Skin butonu -> sonsuz kaydırmalı dialog
skinButton.setOnClickListener {
gameView.pauseGame()

val inflater = LayoutInflater.from(this)
val dialogView = inflater.inflate(R.layout.dialog_skin_slider, null)

val preview = dialogView.findViewById<ImageView>(R.id.previewImage)
val recycler = dialogView.findViewById<RecyclerView>(R.id.skinRecycler)
val okBtn = dialogView.findViewById<Button>(R.id.okButton)
val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)

// skin kaynaklarını tara: skin1..skin500 (ne kadar eklersen o kadar çıkar)
val skins = mutableListOf<Int>()
for (i in 1..500) {
val id = resources.getIdentifier("skin$i", "drawable", packageName)
if (id != 0) skins.add(id)
}
// fallback: eğer hiç skin yoksa default ekle
if (skins.isEmpty()) {
val def = resources.getIdentifier("skin1", "drawable", packageName)
if (def != 0) skins.add(def)
}

// başlangıç seçimi
val prefs = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
var currentIndex = 0
if (skins.isNotEmpty()) {
val sel = prefs.getInt("selected_skin", 1)
currentIndex = skins.indexOfFirst { it == resources.getIdentifier("skin$sel", "drawable", packageName) }
if (currentIndex < 0) currentIndex = 0
preview.setImageResource(skins[currentIndex])
}

// RecyclerView: yatay, sonsuz gösterim
recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
val adapter = object : RecyclerView.Adapter<SkinViewHolder>() {
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkinViewHolder {
val iv = ImageView(parent.context)
val sizePx = (parent.context.resources.displayMetrics.density * 72).toInt()
val lp = RecyclerView.LayoutParams(sizePx, sizePx)
lp.marginStart = 12
lp.marginEnd = 12
iv.layoutParams = lp
iv.scaleType = ImageView.ScaleType.CENTER_CROP
return SkinViewHolder(iv)
}

override fun getItemCount(): Int {
return if (skins.isEmpty()) 0 else Int.MAX_VALUE // sonsuz gösterim
}

override fun onBindViewHolder(holder: SkinViewHolder, position: Int) {
if (skins.isEmpty()) return
val real = skins[position % skins.size]
(holder.itemView as ImageView).setImageResource(real)
holder.itemView.setOnClickListener {
currentIndex = position % skins.size
preview.setImageResource(real)
}
}
}
recycler.adapter = adapter

// Scroll'u başlangıç öğesine getir (large offset ortası)
if (skins.isNotEmpty()) {
val startPos = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % skins.size + currentIndex
recycler.scrollToPosition(startPos)
}

val dialog = AlertDialog.Builder(this)
.setView(dialogView)
.setOnDismissListener {
gameView.resumeGame()
}
.create()

okBtn.setOnClickListener {
if (skins.isNotEmpty()) {
// seçili skin id -> kaynak adı skinX şeklinde bul
val chosenResId = skins[currentIndex]
// bulduğumuz resource id'e karşılık gelen skinX numarasını bulmaya çalış
var chosenIndex = 1
for (i in 1..500) {
val id = resources.getIdentifier("skin$i", "drawable", packageName)
if (id == chosenResId) {
chosenIndex = i
break
}
}
prefs.edit().putInt("selected_skin", chosenIndex).apply()
gameView.setSkin(chosenIndex)
}
dialog.dismiss()
}

cancelBtn.setOnClickListener {
dialog.dismiss()
}

dialog.show()
}
}

// ViewHolder helper
private class SkinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

override fun onResume() {
super.onResume()
gameView.startGame()
}

override fun onPause() {
super.onPause()
gameView.stopGame()
}
}