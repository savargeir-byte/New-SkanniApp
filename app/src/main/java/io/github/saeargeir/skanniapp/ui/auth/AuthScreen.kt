package io.github.saeargeir.skanniapp.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import io.github.saeargeir.skanniapp.firebase.FirebaseAuthService
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authService: FirebaseAuthService,
    onAuthSuccess: () -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    scope.launch {
                        val signInResult = authService.signInWithGoogle(idToken)
                        if (signInResult.isSuccess) {
                            onAuthSuccess()
                        } else {
                            errorMessage = "Google innskráning mistókst: ${signInResult.exceptionOrNull()?.message}"
                        }
                    }
                } else {
                    errorMessage = "Google innskráning mistókst: Ekkert token"
                }
            } catch (e: ApiException) {
                errorMessage = "Google innskráning mistókst: ${e.message}"
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SkanniApp",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Text(
            text = if (isLoginMode) "Innskráning" else "Nýskráning",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                errorMessage = null
            },
            label = { Text("Netfang") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                errorMessage = null
            },
            label = { Text("Lykilorð") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        
        if (!isLoginMode) {
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { 
                    confirmPassword = it
                    errorMessage = null
                },
                label = { Text("Staðfesta lykilorð") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Vinsamlegast fylltu út öll reitina"
                    return@Button
                }
                
                if (!isLoginMode && password != confirmPassword) {
                    errorMessage = "Lykilorðin passa ekki saman"
                    return@Button
                }
                
                if (!isLoginMode && password.length < 6) {
                    errorMessage = "Lykilorð verður að vera að minnsta kosti 6 stafir"
                    return@Button
                }
                
                scope.launch {
                    isLoading = true
                    val result = if (isLoginMode) {
                        authService.signInWithEmailAndPassword(email, password)
                    } else {
                        authService.createUserWithEmailAndPassword(email, password)
                    }
                    
                    if (result.isSuccess) {
                        onAuthSuccess()
                    } else {
                        errorMessage = if (isLoginMode) {
                            "Innskráning mistókst: ${result.exceptionOrNull()?.message}"
                        } else {
                            "Nýskráning mistókst: ${result.exceptionOrNull()?.message}"
                        }
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isLoginMode) "Skrá inn" else "Búa til reikning")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = {
                val signInIntent = authService.getGoogleSignInClient().signInIntent
                googleSignInLauncher.launch(signInIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("🔵 Skrá inn með Google")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { 
                isLoginMode = !isLoginMode
                errorMessage = null
            },
            enabled = !isLoading
        ) {
            Text(
                text = if (isLoginMode) {
                    "Ertu ekki með reikning? Nýskráning"
                } else {
                    "Ertu með reikning? Innskráning"
                }
            )
        }
        
        if (isLoginMode) {
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        errorMessage = "Vinsamlegast sláðu inn netfang"
                        return@TextButton
                    }
                    scope.launch {
                        val result = authService.sendPasswordResetEmail(email)
                        if (result.isSuccess) {
                            errorMessage = "Tölvupóstur sendur til að endurstilla lykilorð"
                        } else {
                            errorMessage = "Villa við að senda tölvupóst: ${result.exceptionOrNull()?.message}"
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Gleymt lykilorð?")
            }
        }
    }
}

@Composable
fun UserProfileCard(
    authService: FirebaseAuthService,
    onSignOut: () -> Unit
) {
    val currentUser by authService.currentUser.collectAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Notandi",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = currentUser?.email ?: "Óþekkt notandi",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    authService.signOut()
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Skrá út")
            }
        }
    }
}