package com.cloudstream.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cloudstream.tv.R
import com.cloudstream.tv.ui.theme.DarkBackground
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

// Represents a floating glowing particle (PlayStation stardust)
data class Particle(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val maxAlpha: Float,
    val driftFreq: Float
)

@Composable
fun IntroScreen(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Phase animations for bezier waves
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    // Logo scaling and alpha animation
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val screenFadeAlpha = remember { Animatable(0f) } // for fade-out

    // Particles system
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var particleTrigger by remember { mutableStateOf(0f) }

    val particleTransition = rememberInfiniteTransition(label = "particles")
    val particlePhase by particleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    // Initialize particles once screen dimensions are estimated
    LaunchedEffect(Unit) {
        val list = mutableListOf<Particle>()
        for (i in 0..40) {
            list.add(
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 0.0005f + Random.nextFloat() * 0.001f,
                    size = 2f + Random.nextFloat() * 6f,
                    maxAlpha = 0.15f + Random.nextFloat() * 0.6f,
                    driftFreq = 0.5f + Random.nextFloat() * 1.5f
                )
            )
        }
        particles = list

        // Start logo entrance
        delay(400)
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200)
        )
        logoScale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(1800)
        )
        
        // Steady state display
        delay(1200)
        
        // Fade out transition
        screenFadeAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800)
        )
        
        onAnimationFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // PlayStation ambient glowing wave and particle canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height * 0.55f

            // 1. Draw dynamic background gradient (cosmic glow)
            val bgGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF131336), // Deep blue-purple
                    Color(0xFF060612)  // Near black
                ),
                center = Offset(width / 2f, height / 2f),
                radius = width * 0.7f
            )
            drawRect(brush = bgGradient)

            // 2. Draw moving bezier waves (horizontal flow)
            val wavePath1 = Path()
            val wavePath2 = Path()
            val wavePath3 = Path()

            wavePath1.moveTo(0f, centerY)
            wavePath2.moveTo(0f, centerY)
            wavePath3.moveTo(0f, centerY)

            for (x in 0..width.toInt() step 5) {
                val xF = x.toFloat()
                
                // Curve 1: Primary cyan wave
                val y1 = centerY + 
                        sin(xF * 0.003f + wavePhase1) * 60f + 
                        sin(xF * 0.007f + wavePhase1 * 1.5f) * 20f
                wavePath1.lineTo(xF, y1)

                // Curve 2: Secondary purple wave
                val y2 = centerY + 
                        sin(xF * 0.004f + wavePhase2) * 50f + 
                        sin(xF * 0.008f + wavePhase2 * 0.8f) * 25f
                wavePath2.lineTo(xF, y2)

                // Curve 3: Subtle accent wave
                val y3 = centerY + 
                        sin(xF * 0.002f + (wavePhase1 + wavePhase2) / 2f) * 40f
                wavePath3.lineTo(xF, y3)
            }

            // Draw paths with glowing colors and alpha overlay
            drawPath(
                path = wavePath2,
                color = Color(0xFF8F5CFF).copy(alpha = 0.22f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
            drawPath(
                path = wavePath1,
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
            )
            drawPath(
                path = wavePath3,
                color = Color(0xFFFFFFFF).copy(alpha = 0.15f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // 3. Draw flowing particles (stardust)
            particles.forEach { p ->
                // Animate coordinates using particlePhase
                val actualY = ((p.y - particlePhase * p.speed) % 1.0f + 1.0f) % 1.0f * height
                val actualX = ((p.x + sin(particlePhase * 0.05f * p.driftFreq) * 0.02f) % 1.0f + 1.0f) % 1.0f * width
                
                // Compute fade edge to make them dissolve
                val edgeFade = if (actualY < 150f) actualY / 150f else if (actualY > height - 150f) (height - actualY) / 150f else 1.0f
                val alpha = p.maxAlpha * edgeFade

                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    radius = p.size,
                    center = Offset(actualX, actualY)
                )
            }
        }



        // Screen black transition fade-out overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = screenFadeAlpha.value))
        )
    }
}
