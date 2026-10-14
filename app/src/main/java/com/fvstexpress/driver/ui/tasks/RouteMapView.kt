package com.fvstexpress.driver.ui.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.RouteResponse
import com.fvstexpress.driver.data.model.Waypoint
import com.fvstexpress.driver.ui.components.LegTag
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.UrgencyFlag
import com.fvstexpress.driver.ui.theme.Fv
import kotlin.math.atan2

/**
 * route-map.html: numbered teardrop pins (▲ pickup / ▼ drop-off, green when done, red ring when
 * urgent), accent route lines with direction arrows, the driver's marker and the "Pin legend" card.
 * Without coordinates (addresses not geocoded yet) the same stops are listed in route order.
 */
@Composable
fun RouteMapView(route: RouteResponse?, onStop: (String) -> Unit, modifier: Modifier = Modifier) {
    val pins = route?.waypoints.orEmpty().filter { it.latitude != null && it.longitude != null }
    Box(modifier.fillMaxSize().background(Fv.MapBg)) {
        if (pins.isEmpty()) RouteList(route?.waypoints.orEmpty(), onStop) else RouteCanvas(route, pins, onStop)
        Legend(Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}

@Composable
private fun RouteCanvas(route: RouteResponse?, pins: List<Waypoint>, onStop: (String) -> Unit) {
    val me = route?.driverPosition
    val lats = pins.map { it.latitude!! } + listOfNotNull(me?.latitude)
    val lngs = pins.map { it.longitude!! } + listOfNotNull(me?.longitude)
    val minLat = lats.min(); val maxLat = lats.max(); val minLng = lngs.min(); val maxLng = lngs.max()
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 70.dp)) {
        val density = LocalDensity.current
        val pad = with(density) { 40.dp.toPx() }
        val w = with(density) { maxWidth.toPx() } - pad * 2
        val h = with(density) { maxHeight.toPx() } - pad * 2
        fun xy(lat: Double, lng: Double): Offset {
            val x = if (maxLng == minLng) 0.5 else (lng - minLng) / (maxLng - minLng)
            val y = if (maxLat == minLat) 0.5 else 1 - (lat - minLat) / (maxLat - minLat)
            return Offset(pad + (x * w).toFloat(), pad + (y * h).toFloat())
        }
        val points = pins.map { xy(it.latitude!!, it.longitude!!) }
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until points.lastIndex) arrowLine(points[i], points[i + 1])
            pins.forEachIndexed { i, p -> pin(points[i], p) }
            me?.let { val o = xy(it.latitude, it.longitude); drawCircle(Fv.Accent, 7.dp.toPx(), o); drawCircle(Color.White, 2.5.dp.toPx(), o) }
        }
        // Tap targets + number badges over each pin.
        pins.forEachIndexed { i, p ->
            val o = points[i]
            val half = with(density) { 14.dp.toPx() }
            Box(Modifier.offset { IntOffset((o.x - half).toInt(), (o.y - half * 2).toInt()) }.size(28.dp).clickable { onStop(p.stopKey) })
            Box(
                Modifier.offset { IntOffset((o.x + half * 0.35f).toInt(), (o.y - half * 2.3f).toInt()) }
                    .size(14.dp).clip(CircleShape).background(Fv.Surface),
                contentAlignment = Alignment.Center,
            ) { T("${p.seq}", 8.sp, Color.White, FontWeight.Bold) }
        }
    }
}

/** A teardrop pin (border-radius 50% 50% 50% 0, rotated −45°) with ▲/▼. */
private fun DrawScope.pin(o: Offset, p: Waypoint) {
    val r = 10.dp.toPx()
    val c = Offset(o.x, o.y - r * 1.45f)
    val color = if (p.done) Fv.Success else Fv.Accent
    val drop = Path().apply {
        moveTo(c.x, o.y)
        lineTo(c.x - r * 0.95f, c.y + r * 0.35f)
        arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), 160f, 220f, false)
        close()
    }
    drawPath(drop, color)
    if (p.urgent) drawCircle(Fv.Danger, r + 2.dp.toPx(), c, style = Stroke(2.dp.toPx()))
    val s = r * 0.45f
    val tri = Path().apply {
        if (p.leg == Leg.PICKUP) { moveTo(c.x, c.y - s); lineTo(c.x + s, c.y + s * 0.7f); lineTo(c.x - s, c.y + s * 0.7f) }
        else { moveTo(c.x, c.y + s); lineTo(c.x + s, c.y - s * 0.7f); lineTo(c.x - s, c.y - s * 0.7f) }
        close()
    }
    drawPath(tri, Color.White)
}

/** Route segment with an arrowhead halfway along. */
private fun DrawScope.arrowLine(a: Offset, b: Offset) {
    drawLine(Fv.Accent, a, b, strokeWidth = 2.dp.toPx())
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
    val s = 6.dp.toPx()
    rotate(angle, mid) {
        drawPath(Path().apply { moveTo(mid.x + s, mid.y); lineTo(mid.x - s, mid.y - s * 0.8f); lineTo(mid.x - s, mid.y + s * 0.8f); close() }, Fv.Accent)
    }
}

/** No coordinates yet: the route in order, styled like the task list. */
@Composable
private fun RouteList(waypoints: List<Waypoint>, onStop: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Fv.Bg).verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        if (waypoints.isEmpty()) T("No stops on today's route.", 12.5.sp, Fv.TextFaint, modifier = Modifier.padding(24.dp))
        waypoints.forEach { w ->
            Row(
                Modifier.fillMaxWidth().clickable { onStop(w.stopKey) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (w.done) Fv.Success else Fv.Accent), contentAlignment = Alignment.Center) {
                    T("${w.seq}", 11.sp, Color.White, FontWeight.Bold)
                }
                if (w.urgent) UrgencyFlag()
                LegTag(w.leg)
                T(w.address ?: w.stopKey, 13.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (w.orderCount > 1) T("${w.orderCount} orders", 11.5.sp, Fv.TextFaint)
                T("›", 16.sp, Fv.TextFaint)
            }
            Rule()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(modifier: Modifier) {
    val shape = RoundedCornerShape(Fv.RadiusMd)
    Column(modifier.fillMaxWidth().clip(shape).background(Fv.Surface).border(1.dp, Fv.Border, shape)) {
        T("Pin legend", 11.5.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        Rule()
        FlowRow(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(Fv.Accent, "▲ Pickup / ▼ Dropoff")
            LegendItem(Fv.Success, "Done")
            LegendItem(null, "Urgent")
            LegendItem(Fv.Accent, "You are here", round = true)
        }
    }
}

@Composable
private fun LegendItem(color: Color?, label: String, round: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (color == null) Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, Fv.Danger, CircleShape))
        else Box(Modifier.size(10.dp).clip(if (round) CircleShape else RoundedCornerShape(50, 50, 0, 50)).background(color))
        Spacer(Modifier.width(5.dp))
        T(label, 11.5.sp)
    }
}
