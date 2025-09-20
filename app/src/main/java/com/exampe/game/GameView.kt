package com.example.game

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class GameView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), Runnable {

// Thread / çizim
private var thread: Thread? = null
private var running = false
private val holderSurface: SurfaceHolder = holder
private val paint = Paint()

// Player (128x128 sabit sprite)
private val playerWidth = 128
private val playerHeight = 128
private var playerX = 0f      // yatayda ortalanacak (post{})
private var playerY = 0f      // baseY = ekran ortası (sprite top)
private val playerRect = Rect()

// Game state
var score = 0
var highScore = 0
var isGameOver = false
var isPaused = false

// Jump / crouch
private var jumping = false
private var jumpVelocity = 0f
private var gravity = 7f             // düşüş hızı (ayarlanabilir)
private var crouching = false

// Obstacles
private val obstacles = mutableListOf<Rect>()
private var obstacleActive = true
private var handler: Handler? = null
private var obstacleRunnable: Runnable? = null

// Hız / zorluk
private val baseObstacleSpeed = 18
private var obstacleSpeed = baseObstacleSpeed

// revive protection
private var reviveProtection = false

// Skin / bitmap
private var selectedSkin = 1
private var characterBitmap: Bitmap = Bitmap.createBitmap(playerWidth, playerHeight, Bitmap.Config.ARGB_8888)
private var characterScaled: Bitmap = characterBitmap

init {
// başlangıç için load skin (MainActivity setSkin ile değiştirebilir)
selectedSkin = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE).getInt("selected_skin", 1)
setSkin(selectedSkin)

// layout hazır olduğunda ortala ve spawn başlat
post {
playerX = ((width - playerWidth) / 2f).coerceAtLeast(50f) // yatay ortala
val baseY = ((height - playerHeight) / 2f).coerceAtLeast(200f) // ekran ortası
playerY = baseY
updatePlayerRect()
loadHighScore()
startSpawningObstacles()
}

// dokununca zıpla (alternatif: MainActivity butonu)
setOnTouchListener { _, event ->
if (event.action == MotionEvent.ACTION_DOWN && !isGameOver && !isPaused) {
if (!jumping && !crouching) {
// eskisi gibi daha yüksek zıplama
val jumpPower = -80f - (score / 10f)
jumping = true
jumpVelocity = jumpPower
}
}
true
}
}


/** runtime skin değişimi */
fun setSkin(skinId: Int) {
selectedSkin = skinId
val res = when (skinId) {
2 -> R.drawable.skin2
3 -> R.drawable.skin3
else -> {
// default skin: skin1
val id = resources.getIdentifier("skin1", "drawable", context.packageName)
if (id != 0) id else R.drawable.skin1
}
}
try {
// Her zaman 128x128 ölçekle
characterBitmap = BitmapFactory.decodeResource(resources, res)
characterScaled = Bitmap.createScaledBitmap(characterBitmap, playerWidth, playerHeight, true)
} catch (e: Exception) {
// fallback: boş bitmap
characterBitmap = Bitmap.createBitmap(playerWidth, playerHeight, Bitmap.Config.ARGB_8888)
characterScaled = characterBitmap
}
}

private fun updatePlayerRect() {
val top = playerY.toInt()
val bottom = (playerY + playerHeight).toInt()
playerRect.set(playerX.toInt(), top, (playerX + playerWidth).toInt(), bottom)
}

override fun run() {
while (running) {
if (!isPaused && !isGameOver) {
update()
drawCanvas()
}
try {
Thread.sleep(16)
} catch (_: InterruptedException) {
}
}
}

fun startGame() {
if (thread == null || !thread!!.isAlive) {
running = true
thread = Thread(this)
thread!!.start()
}
}

fun stopGame() {
running = false
try {
thread?.join()
} catch (_: InterruptedException) {
}
thread = null
}

private fun update() {
// baseY = ekran ortası (player in normal konumu)
val baseY = ((height - playerHeight) / 2f).coerceAtLeast(200f)

// zıplama / yerçekimi (baseY'ye dönüyor)
if (jumping) {
playerY += jumpVelocity
jumpVelocity += gravity
if (playerY >= baseY) {
playerY = baseY
jumping = false
jumpVelocity = 0f
}
if (playerY < 0f) {
playerY = 0f
jumpVelocity = gravity
}
} else {
// eğer baseY'nin üstündeyse, yavaşça baseY'ye dön
if (playerY < baseY) {
playerY = (playerY + gravity).coerceAtMost(baseY)
} else {
playerY = baseY
}
}

updatePlayerRect()

// Engelleri güncelle: engeller baseY etrafında doğurulacak
val it = obstacles.iterator()
while (it.hasNext()) {
val r = it.next()
r.offset(-obstacleSpeed, 0)

// collision rect: crouch durumunda üst kısm kısalır (alt sabit)
val collisionRect = if (crouching) {
Rect(playerRect.left, playerRect.top + playerHeight / 2, playerRect.right, playerRect.bottom)
} else {
Rect(playerRect)
}

if (!reviveProtection && Rect.intersects(collisionRect, r)) {
isGameOver = true
saveHighScore()
handler?.removeCallbacks(obstacleRunnable ?: return)
}

if (r.right < 0) {
it.remove()
score += 1
}
}

// hız zorluğa göre artsın
obstacleSpeed = baseObstacleSpeed + (score / 5)
}

private fun drawCanvas() {
if (!holderSurface.surface.isValid) return
val canvas = holderSurface.lockCanvas()
try {
canvas.drawColor(Color.WHITE)

// Karakter: eğilme durumunda alt sabit, üst üstten kırpılmış şekilde çiz
if (crouching) {
// hedef rect'in üstünü yarıya çekiyoruz
val drawRect = Rect(
playerRect.left,
playerRect.top + playerHeight / 2,
playerRect.right,
playerRect.bottom
)
canvas.drawBitmap(characterScaled, null, drawRect, null)
} else {
canvas.drawBitmap(characterScaled, playerRect.left.toFloat(), playerRect.top.toFloat(), null)
}

// Engeller (orta çizgi etrafında doğuyorlar)
paint.color = Color.RED
for (r in obstacles) {
canvas.drawRect(r, paint)
}
} finally {
holderSurface.unlockCanvasAndPost(canvas)
}
}

override fun onTouchEvent(event: MotionEvent): Boolean {
if (event.action == MotionEvent.ACTION_DOWN) {
if (!isGameOver && !isPaused && !jumping && !crouching) {
val jumpPower = -70f - (score / 10f) // yüksek zıplama
jumping = true
jumpVelocity = jumpPower
}
}
return true
}

private fun startSpawningObstacles() {
handler?.removeCallbacks(obstacleRunnable ?: return)
handler = Handler(Looper.getMainLooper())
obstacleRunnable = object : Runnable {
override fun run() {
if (!isGameOver && !isPaused && obstacleActive) {
// Engel boyutu skora göre (%50 büyütülmüş başlangıç)
val baseSize = (60 * 1.5).toInt()   // 90
val maxSize = (200 * 1.5).toInt()   // 300
val growth = (score / 5) * 5
val size = (baseSize + growth).coerceAtMost(maxSize)

// Engeli baseY etrafında doğur (orta çizgi)
val baseY = ((height - playerHeight) / 2f).coerceAtLeast(200f)
val top = (baseY + playerHeight / 2 - size / 2).toInt().coerceAtLeast(0)
val bottom = top + size
val rect = Rect(width, top, width + size, bottom)
obstacles.add(rect)
}

val minDelay = (1200L - score * 10).coerceAtLeast(400L)
val maxDelay = (2200L - score * 10).coerceAtLeast(900L)
val nextDelay = Random.nextLong(minDelay, maxDelay)
handler?.postDelayed(this, nextDelay)
}
}
handler?.post(obstacleRunnable!!)
}

// controllerlar
fun crouchDown() { crouching = true }
fun crouchUp() { crouching = false }

fun pauseGame() {
isPaused = true
handler?.removeCallbacks(obstacleRunnable ?: return)
}

fun resumeGame() {
if (!isGameOver) {
isPaused = false
startSpawningObstacles()
}
}

fun resetGame() {
score = 0
isGameOver = false
obstacles.clear()
obstacleActive = true
jumping = false
jumpVelocity = 0f
crouching = false
val baseY = ((height - playerHeight) / 2f).coerceAtLeast(200f)
playerY = baseY
updatePlayerRect()
obstacleSpeed = baseObstacleSpeed
startSpawningObstacles()
}

fun revivePlayer() {
if (isGameOver) {
isGameOver = false
reviveProtection = true
obstacles.clear()
handler?.postDelayed({
reviveProtection = false
obstacleActive = true
}, 3000)
startSpawningObstacles()
}
}

// HighScore (şifreli)
private fun saveHighScore() {
if (score > highScore) {
highScore = score
val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
val enc = (highScore * 73) + 1234
prefs.edit().putInt("high_score_enc", enc).apply()
}
}

private fun loadHighScore() {
val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
val enc = prefs.getInt("high_score_enc", 0)
highScore = if (enc != 0) (enc - 1234) / 73 else 0
}
}