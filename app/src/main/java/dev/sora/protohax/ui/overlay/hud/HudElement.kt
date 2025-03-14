package dev.sora.protohax.ui.overlay.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.MotionEvent
import androidx.core.graphics.withTranslation
import dev.sora.protohax.MyApplication
import dev.sora.protohax.ui.overlay.RenderLayerView
import dev.sora.protohax.util.ContextUtils.getColor
import dev.sora.relay.cheat.value.Configurable
import dev.sora.relay.cheat.value.Value
import dev.sora.relay.game.event.EventHook
import dev.sora.relay.game.event.EventManager
import dev.sora.relay.game.event.GameEvent
import dev.sora.relay.game.event.Handler
import org.cloudburstmc.math.vector.Vector2f
import java.util.concurrent.atomic.AtomicBoolean

abstract class HudElement(val name: String) : Configurable {

	override val values = mutableListOf<Value<*>>()
	protected val handlers = mutableListOf<EventHook<in GameEvent>>()

	var posX = 100
	var posY = 100
	var alignmentValue by listValue("Alignment", HudAlignment.values(), HudAlignment.LEFT_TOP)
	var fontValue by listValue("Font", HudFont.values(), HudFont.DEFAULT)
	var blurValue by boolValue("Blur", true)
	var blurRadiusValue by floatValue("Blur Radius", 1f, 0f..80f).visible { blurValue }
	var blurModeValue by listValue("Blur Mode", HudBlurMode.values(), HudBlurMode.NORMAL).visible { blurValue }
	var shadowRadiusValue by floatValue("Shadow Radius", 0f, 0f..10f)
	var shadowAlphaValue by intValue("Shadow Alpha", 160, 0..255)


	abstract val height: Float
	abstract val width: Float

	// cache the value to avoid frequent instance creation
	private val needRefresh = AtomicBoolean()

	private var dragging = false
	private var dragPointerOffsetX = 0
	private var dragPointerOffsetY = 0

	abstract fun onRender(canvas: Canvas, editMode: Boolean, needRefresh: AtomicBoolean, context: Context)

	open fun getPosition(canvasWidth: Int, canvasHeight: Int): Vector2f {
		val position = alignmentValue.getPosition(canvasWidth, canvasHeight)
		return Vector2f.from(
			(position.x + posX + width).coerceAtMost(canvasWidth.toFloat()) - width,
			(position.y + posY + height).coerceAtMost(canvasHeight.toFloat()) - height
		)
	}

	open fun setPosition(canvasWidth: Int, canvasHeight: Int, x: Int, y: Int) {
		val position = alignmentValue.getPosition(canvasWidth, canvasHeight)

		posX = x - position.x
		posY = y - position.y
	}

	private val handleRender = handle<RenderLayerView.EventRender> {
		this@HudElement.needRefresh.set(false)
		getPosition(canvas.width, canvas.height).also {
			canvas.withTranslation(it.x, it.y) {
				onRender(this, editMode, this@HudElement.needRefresh, context)
			}
			if (editMode) {
				drawBorder(canvas, it)
			}
		}
		if (this@HudElement.needRefresh.get()) {
			needRefresh = true
		}
	}

	protected open fun drawBorder(canvas: Canvas, position: Vector2f) {
		val paint = Paint().apply {
			style = Paint.Style.STROKE
			val context = MyApplication.instance
			color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				context.getColor(context.getColor(android.R.color.system_accent1_600, android.R.color.system_accent1_200))
			} else context.getColor(Color.rgb(103, 80, 164), Color.rgb(208, 188, 255))
			strokeWidth = 10f
		}

		canvas.drawRoundRect(
			position.x - 5f, position.y - 5f, position.x + width + 5f, position.y + height + 5f,
			15f, 15f, paint
		)
	}

	/**
	 * handles drag
 	 */
	private val handleRenderLayerMotion = handle<RenderLayerView.EventRenderLayerMotion> {
		if (hasHandled) {
			return@handle
		}

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				val position = getPosition(view.width, view.height)
				if (event.x >= position.x && event.y >= position.y && event.x <= position.x + width && event.y <= position.y + height) {
					dragging = true
					dragPointerOffsetX = (event.x - position.x).toInt()
					dragPointerOffsetY = (event.y - position.y).toInt()
				}
			}
			MotionEvent.ACTION_UP -> {
				dragging = false
			}
			MotionEvent.ACTION_MOVE -> {
				if (dragging) {
					setPosition(view.width, view.height, event.x.toInt() - dragPointerOffsetX, event.y.toInt() - dragPointerOffsetY)
				}
			}
		}

		if (dragging) {
			hasHandled = true
		}
	}

	@Suppress("unchecked_cast")
	protected inline fun <reified T : GameEvent> handle(noinline handler: Handler<T>) {
		handlers.add(EventHook(T::class.java, handler) as EventHook<in GameEvent>)
	}

	fun register(eventManager: EventManager) {
		handlers.forEach(eventManager::register)
	}

	fun unregister(eventManager: EventManager) {
		handlers.forEach(eventManager::removeHandler)
	}
}
