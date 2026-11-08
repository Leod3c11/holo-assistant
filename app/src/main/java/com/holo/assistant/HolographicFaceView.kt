package com.holo.assistant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import java.util.Random

class HolographicFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paleta fiel à imagem de referência
    private val colorBg = Color.parseColor("#080D15")
    private val colorVoxelCore = Color.parseColor("#F0F8FF")
    private val colorVoxelMid = Color.parseColor("#7EB8DA")
    private val colorVoxelDeep = Color.parseColor("#263947")
    private val colorVoxelGreen = Color.parseColor("#00E676")
    private val colorVoxelAmber = Color.parseColor("#FFD54F")

    private val voxelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#90A4AE")
        textSize = 24f
    }

    data class Voxel(
        var x: Float, var y: Float, var z: Float,
        val ox: Float, val oy: Float, val oz: Float,
        val isDispersal: Boolean,
        var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
        val size: Float = 4.2f
    )

    private val voxels = ArrayList<Voxel>()
    private val random = Random(42)

    var isSpeaking: Boolean = false
    var emotion: Int = 0 // 0 = Neutro, 1 = Feliz, 2 = Pensando
    var headYaw: Float = 0f
    var headPitch: Float = 0f
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0

    var lastSpokenText: String = "TOQUE NA TELA PARA FALAR COMIGO"

    init {
        generateParticleCloud()
    }

    private fun generateParticleCloud() {
        voxels.clear()

        // 1. Núcleo Anatômico do Rosto (grade densa de ~880 voxels 3D)
        val stepsY = 36
        val stepsX = 26

        for (iy in 0 until stepsY) {
            val y = -145f + (iy.toFloat() / (stepsY - 1)) * 290f
            val normY = (y + 10f) / 155f
            val wFactor = if (abs(normY) < 1f) sqrt(1f - normY * normY) else 0.2f
            val maxX = 115f * wFactor

            for (ix in 0 until stepsX) {
                val x = -maxX + (ix.toFloat() / (stepsX - 1)) * (2f * maxX)
                val radSq = (x / 125f).pow(2) + normY.pow(2)
                var z = sqrt(max(0f, 1f - radSq * 0.75f)) * 75f

                // Relevo anatômico: Nariz
                if (abs(x) < 22f && y in -25f..35f) {
                    z += 48f * exp(-((x / 14f).pow(2) + ((y - 8f) / 22f).pow(2)))
                }
                // Cavidades oculares (reentrância)
                if (abs(x) in 25f..75f && y in -45f..-10f) {
                    z -= 28f * exp(-(((abs(x) - 50f) / 16f).pow(2) + ((y + 28f) / 14f).pow(2)))
                }
                // Lábios e boca
                if (abs(x) < 45f && y in 65f..115f) {
                    if (y in 80f..95f) {
                        z += 20f * exp(-((x / 22f).pow(2) + ((y - 88f) / 10f).pow(2)))
                    } else if (y in 95f..112f) {
                        z += 17f * exp(-((x / 22f).pow(2) + ((y - 104f) / 10f).pow(2)))
                    }
                }
                // Queixo
                if (abs(x) < 35f && y in 155f..195f) {
                    z += 26f * exp(-((x / 20f).pow(2) + ((y - 175f) / 18f).pow(2)))
                }

                val noise = (random.nextFloat() - 0.5f) * 3f
                voxels.add(Voxel(x, y, z + noise, x, y, z, false, size = 4.2f))
            }
        }

        // 2. Poeira de Dispersão Externa (~450 partículas flutuantes ao redor)
        for (i in 0 until 450) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val radius = 105f + random.nextFloat() * 145f
            val x = cos(angle) * radius
            val y = (random.nextFloat() - 0.5f) * 320f
            val z = (random.nextFloat() - 0.5f) * 120f

            voxels.add(
                Voxel(
                    x, y, z, x, y, z, true,
                    vx = (random.nextFloat() - 0.5f) * 0.6f,
                    vy = (random.nextFloat() - 0.5f) * 0.6f,
                    vz = (random.nextFloat() - 0.5f) * 0.6f,
                    size = 2.4f + random.nextFloat() * 2.5f
                )
            )
        }
    }

    fun updateFaceTracking(smileProb: Float, yaw: Float, pitch: Float) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        headYaw += ((-yaw * (PI.toFloat() / 180f) * 0.9f) - headYaw) * 0.2f
        headPitch += ((pitch * (PI.toFloat() / 180f) * 0.7f) - headPitch) * 0.2f

        if (!isSpeaking) {
            emotion = if (smileProb > 0.45f) 1 else 0
        }
    }

    fun resetTracking() {
        isFaceLocked = false
        userSmilePct = 0
        headYaw += (0f - headYaw) * 0.1f
        headPitch += (0f - headPitch) * 0.1f
        if (!isSpeaking) emotion = 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h / 2f - 30f
        val time = SystemClock.uptimeMillis()

        canvas.drawColor(colorBg)

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val fov = 520f

        val mouthWave = if (isSpeaking) (sin(time / 85.0).toFloat().absoluteValue * 14f) else 0f
        val smileMod = if (emotion == 1) -10f else 0f

        // Renderizar a Nuvem de Voxels 3D
        for (v in voxels) {
            var currX = v.x
            var currY = v.y
            var currZ = v.z

            if (v.isDispersal) {
                // Partículas flutuantes em movimento
                currX += v.vx
                currY += v.vy
                currZ += v.vz
                v.x = currX
                v.y = currY
                v.z = currZ

                // Wrap around
                if (currX < -250f || currX > 250f) v.vx *= -1f
                if (currY < -240f || currY > 240f) v.vy *= -1f
                if (currZ < -100f || currZ > 120f) v.vz *= -1f
            } else {
                // Articulação labial e de humor
                if (currY in 75f..115f && abs(currX) < 45f) {
                    currY += mouthWave
                }
                if (emotion == 1 && currY in 40f..110f && abs(currX) in 30f..85f) {
                    currY += smileMod
                }
            }

            // Rotação 3D Yaw (horizontal)
            val x1 = currX * cosY + currZ * sinY
            val z1 = -currX * sinY + currZ * cosY

            // Rotação 3D Pitch (vertical)
            val y2 = currY * cosX - z1 * sinX
            val z2 = currY * sinX + z1 * cosX

            // Projeção perspectiva
            val scale = fov / (fov + z2 + 110f)
            val px = cx + x1 * scale
            val py = cy + y2 * scale

            // Iluminação por profundidade (Near = Brilho intenso, Far = Aço escuro)
            val depthRatio = ((z2 + 70f) / 150f).coerceIn(0f, 1f)

            val voxelColor = when (emotion) {
                1 -> colorVoxelGreen
                2 -> colorVoxelAmber
                else -> {
                    if (depthRatio > 0.65f) colorVoxelCore
                    else if (depthRatio > 0.35f) colorVoxelMid
                    else colorVoxelDeep
                }
            }

            val alpha = (110 + (depthRatio * 145)).toInt().coerceIn(40, 255)
            voxelPaint.color = voxelColor
            voxelPaint.alpha = alpha

            val drawSize = (v.size * scale * (0.6f + depthRatio * 0.6f)).coerceAtLeast(1.5f)
            canvas.drawRect(px - drawSize, py - drawSize, px + drawSize, py + drawSize, voxelPaint)
        }

        drawHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawHUD(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#78909C")
        textPaint.textSize = 24f

        canvas.drawText("HOLO // QUANTUM VOXEL CORE", 40f, 85f, textPaint)

        if (emotion == 2) {
            textPaint.color = colorVoxelAmber
            canvas.drawText("STATUS: PENSANDO...", 40f, 120f, textPaint)
        } else if (isFaceLocked) {
            textPaint.color = Color.parseColor("#80DEEA")
            canvas.drawText("SENSOR: RASTREIO ATIVO [$userSmilePct% SORRISO]", 40f, 120f, textPaint)
        } else {
            textPaint.color = Color.parseColor("#546E7A")
            canvas.drawText("SENSOR: BUSCANDO ROSTO...", 40f, 120f, textPaint)
        }

        // Ícone de chave no canto superior direito para configurar API
        textPaint.color = Color.parseColor("#4DD0E1")
        val configBtn = "[CONFIGURAR IA]"
        val btnWidth = textPaint.measureText(configBtn)
        canvas.drawText(configBtn, w - btnWidth - 40f, 85f, textPaint)

        // Texto falado no rodapé
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        val textWidth = textPaint.measureText(lastSpokenText)
        canvas.drawText(lastSpokenText, (w - textWidth).coerceAtLeast(40f) / 2f, h - 80f, textPaint)
    }
}
