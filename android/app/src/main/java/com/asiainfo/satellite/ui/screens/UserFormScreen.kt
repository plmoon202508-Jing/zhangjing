package com.asiainfo.satellite.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.ui.theme.Cyan
import com.asiainfo.satellite.ui.theme.TextDim
import com.asiainfo.satellite.ui.theme.TextMain
import com.asiainfo.satellite.ui.theme.Violet

@Composable
fun UserFormScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    HoloScaffold("体验登记", onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 10.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("开启你的卫星之旅", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("请预留信息，我们将为你解锁完整体验", color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))

            HoloField("姓名 *", name, { name = it }, "请输入姓名")
            Spacer(Modifier.height(18.dp))
            HoloField("手机号 *", phone, { phone = it }, "请输入手机号", KeyboardType.Phone)
            Spacer(Modifier.height(18.dp))
            HoloField("邮箱", email, { email = it }, "请输入邮箱", KeyboardType.Email)
            Spacer(Modifier.height(18.dp))
            HoloField("公司 / 单位", company, { company = it }, "选填")

            Spacer(Modifier.height(20.dp))
            Text(
                "提交即表示同意隐私政策，信息仅用于改善产品体验。",
                color = TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        Toast.makeText(ctx, "请填写姓名和手机号", Toast.LENGTH_SHORT).show()
                    } else {
                        // TODO: 提交到后端；当前仅本地演示
                        Toast.makeText(ctx, "登记成功，正在进入体验...", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text("提交并开始体验", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HoloField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = TextDim.copy(alpha = 0.5f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = TextDim.copy(alpha = 0.3f),
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                cursorColor = Cyan
            )
        )
    }
}
