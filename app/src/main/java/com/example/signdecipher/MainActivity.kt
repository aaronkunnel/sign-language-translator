package com.example.signdecipher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signdecipher.ui.theme.SignDecipherTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraApp()
                }
            }
        }
    }
}

@Composable
fun CameraApp() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Respect Scaffold padding
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sign Decipher",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            StartCameraButton()
        }
    }
}

@Composable
fun StartCameraButton() {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(context, CameraActivity::class.java)
            context.startActivity(intent)
        },
        modifier = Modifier
            .width(200.dp)
            .padding(vertical = 16.dp)
    ) {
        Text("Start Camera")
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    SignDecipherTheme {
        CameraApp()
    }
}