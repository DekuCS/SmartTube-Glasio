package io.github.ivanosh.stmaterial.signin

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme
import io.github.ivanosh.stmaterial.ui.Palette

/**
 * The code to type at Google's activation page. Google creates the code and the address; they are shown here
 * with a QR code of the link that carries the code, so a phone opens the page with the code filled in. The QR is
 * drawn on the device (no picture service sees the link) and there is no browser. The presenter waits for the
 * sign-in and calls [close]; Back leaves without signing in.
 *
 * This is no part of the view stack of the app, so closing returns to the screen that opened it.
 */
class MaterialSignInActivity : MotherActivity(), SignInView {
    private lateinit var presenter: SignInPresenter
    private var code by mutableStateOf<String?>(null)
    private var address by mutableStateOf("")
    private var qrLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply { setContent { MaterialTvTheme { SignInScreen(code, address, qrLink) } } })
        window.setBackgroundDrawable(null)
        presenter = SignInPresenter.instance(this)
        presenter.setView(this)
        presenter.onViewInitialized()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onViewDestroyed()
    }

    override fun showCode(userCode: String?, signInUrl: String?) {
        code = userCode?.takeIf { it.isNotBlank() }
        address = signInUrl.orEmpty()
        qrLink = signInQrLink(null, signInUrl)
    }

    /** The full link has the code in it; a refreshed code brings a new link and so a new QR. */
    override fun showCode(userCode: String?, signInUrl: String?, fullSignInUrl: String?) {
        showCode(userCode, signInUrl)
        if (!userCode.isNullOrBlank()) qrLink = signInQrLink(fullSignInUrl, signInUrl)
    }

    override fun close() {
        runOnUiThread { finishReally() }
    }
}

private val CodeStyle = TextStyle(color = Palette.OnBackground, fontSize = 150.sp, lineHeight = 170.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
private val AddressStyle = TextStyle(color = Palette.Accent, fontSize = 72.sp, lineHeight = 84.sp, fontWeight = FontWeight.Medium)
private val LineStyle = TextStyle(color = Palette.OnBackgroundVariant, fontSize = 34.sp, lineHeight = 44.sp, textAlign = TextAlign.Center)

@Composable
private fun SignInScreen(code: String?, address: String, qrLink: String?) {
    Box(Modifier.fillMaxSize().background(Palette.Background).padding(64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
            when {
                code == null -> Text(stringResource(R.string.material_signin_getting), style = LineStyle)
                // On a failure the presenter puts the message where the code goes and gives no address.
                address.isBlank() -> {
                    Text(code, style = LineStyle.copy(color = Palette.OnBackground, fontSize = 40.sp))
                    Text(stringResource(R.string.material_signin_failed_hint), style = LineStyle)
                }
                else -> {
                    val qr = rememberQr(qrLink)
                    Text(stringResource(if (qr != null) R.string.material_signin_scan else R.string.material_signin_instruction), style = LineStyle)
                    Row(horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
                            Text(address, style = AddressStyle)
                            Text(code, style = CodeStyle)
                        }
                        // Dark modules on white, with the quiet zone inside the picture, whatever the theme.
                        if (qr != null) Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(300.dp), filterQuality = FilterQuality.None)
                    }
                }
            }
        }
    }
}

/** The QR bitmap of [link]; computed off the main thread, kept while the link stays the same, null when there is none or it fails. */
@Composable
private fun rememberQr(link: String?): Bitmap? = produceState<Bitmap?>(null, link) {
    value = withContext(Dispatchers.Default) {
        signInQrMatrix(link)?.let { m -> Bitmap.createBitmap(signInQrPixels(m, 8), m.width * 8, m.height * 8, Bitmap.Config.ARGB_8888) }
    }
}.value
