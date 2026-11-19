package id.tukangkilat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tukangkilat.app.ui.theme.TKColor

@Composable
fun TKPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TKColor.Orange,
            contentColor = TKColor.White,
            disabledContainerColor = TKColor.Orange.copy(alpha = 0.4f),
            disabledContentColor = TKColor.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.3.sp)
    }
}

@Composable
fun TKSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TKColor.White,
            contentColor = TKColor.Ink
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun TKTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TKColor.Orange,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    )
}

@Composable
fun TKCard(
    modifier: Modifier = Modifier,
    color: Color = TKColor.Card,
    cornerRadius: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content
    )
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = TKColor.Orange)
    }
}

@Composable
fun EmptyView(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Icon(Icons.Filled.Inbox, null, tint = TKColor.Mist, modifier = Modifier.size(48.dp)) }
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = TKColor.Mist, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorView(
    modifier: Modifier = Modifier,
    message: String = "Terjadi kesalahan",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = TKColor.Mist, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Gagal memuat data", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = TKColor.Mist, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            TKPrimaryButton("Coba lagi", onRetry, modifier = Modifier.width(200.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TKColor.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Kembali", tint = TKColor.White)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            color = TKColor.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
    }
}

@Composable
fun ModeTukangChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TKColor.Orange)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("MODE TUKANG", color = TKColor.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun AvatarCircle(name: String, size: Int = 44, photoUrl: String? = null) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(TKColor.Orange),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.5).sp)
    }
}

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, starSize: Int = 18) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = rating >= i - 0.25
            Icon(
                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = TKColor.Orange,
                modifier = Modifier.size(starSize.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "%.1f".format(rating),
            color = TKColor.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun TextFieldDark(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    Column(modifier) {
        if (label != null) {
            Text(label, color = TKColor.Mist, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(TKColor.Card, RoundedCornerShape(14.dp)),
            placeholder = placeholder?.let { { Text(it, color = Color(0xFF8A94A6)) } },
            minLines = minLines,
            maxLines = if (singleLine) 1 else 8,
            singleLine = singleLine,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TKColor.Orange,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = TKColor.Red,
                focusedTextColor = TKColor.Ink,
                unfocusedTextColor = TKColor.Ink,
                focusedContainerColor = TKColor.Card,
                unfocusedContainerColor = TKColor.Card,
                errorContainerColor = TKColor.Card,
                cursorColor = TKColor.Orange
            )
        )
    }
}
