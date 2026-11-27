package com.pokerlanka.pokie_talkie.ui.chat

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

private class RichContentEditText(context: Context) : EditText(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        val mimeTypes = ViewCompat.getOnReceiveContentMimeTypes(this) ?: return ic
        EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes)
        return InputConnectionCompat.createWrapper(this, ic, outAttrs)
    }
}

/**
 * EditText backed input supporting full system keyboard emojis, GIFs, and stickers
 * via ViewCompat ReceiveContent / InputConnectionCompat across Android 8+.
 */
@Composable
fun RichContentChatInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onMediaReceived: (Uri, String?) -> Unit,
    placeholder: String = "Message (emojis, GIFs, stickers)...",
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val cornerRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val horizPadPx = with(LocalDensity.current) { 16.dp.toPx().toInt() }
    val vertPadPx = with(LocalDensity.current) { 10.dp.toPx().toInt() }

    val latestOnTextChanged by rememberUpdatedState(onTextChanged)
    val latestOnMediaReceived by rememberUpdatedState(onMediaReceived)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            RichContentEditText(ctx).apply {
                hint = placeholder
                setTextColor(textColor)
                setHintTextColor(hintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                maxLines = 4
                isVerticalScrollBarEnabled = true
                setPadding(horizPadPx, vertPadPx, horizPadPx, vertPadPx)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    setColor(bgColor)
                }

                ViewCompat.setOnReceiveContentListener(
                    this,
                    arrayOf("image/gif", "image/webp", "image/png", "image/jpeg", "image/*")
                ) { _, payload ->
                    val split = payload.partition { item -> item.uri != null }
                    val uriContent = split.first
                    val remaining = split.second
                    if (uriContent != null) {
                        val clip = uriContent.clip
                        for (i in 0 until clip.itemCount) {
                            val itemUri = clip.getItemAt(i).uri
                            if (itemUri != null) {
                                val mime = ctx.contentResolver.getType(itemUri)
                                latestOnMediaReceived(itemUri, mime)
                            }
                        }
                    }
                    remaining
                }

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val current = s?.toString() ?: ""
                        if (tag != current) {
                            latestOnTextChanged(current)
                        }
                    }
                })
            }
        },
        update = { editText ->
            editText.setTextColor(textColor)
            editText.setHintTextColor(hintColor)
            (editText.background as? GradientDrawable)?.setColor(bgColor)
            if (editText.text?.toString() != text) {
                editText.tag = text
                editText.setText(text)
                editText.setSelection(text.length)
                editText.tag = null
            }
        }
    )
}
