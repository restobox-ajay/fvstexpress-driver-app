package com.fvstexpress.driver.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fvstexpress.driver.data.model.TimeWindow
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA)

/** "13:05" or an ISO date-time -> "1:05 PM". Returns the input unchanged if it can't be parsed. */
fun timeLabel(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching { LocalTime.parse(value).format(clock) }
        .recoverCatching { OffsetDateTime.parse(value).toLocalTime().format(clock) }
        .getOrDefault(value)
}

fun windowLabel(w: TimeWindow?): String = when {
    w == null -> ""
    w.start != null && w.end != null -> "${timeLabel(w.start)} – ${timeLabel(w.end)}"
    w.end != null -> "By ${timeLabel(w.end)}"
    else -> timeLabel(w.start)
}

fun minutesLabel(minutes: Int): String = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

fun Context.dial(phone: String?) {
    if (phone.isNullOrBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.text(phone: String?) {
    if (phone.isNullOrBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens turn-by-turn in Google Maps (or any maps app) for an address. */
fun Context.navigateTo(address: String) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(address))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(nav) }.recoverCatching { startActivity(geo) }
}
