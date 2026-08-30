package com.example.rezepte

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.rezepte.ui.theme.RezepteTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import java.util.Collections

class MainActivity : ComponentActivity() {
    private val viewModel: RecipeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RezepteTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: RecipeViewModel) {
    val context = LocalContext.current
    val recipes by viewModel.recipes.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var signedInAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            signedInAccount = account
            if (account != null) {
                val credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singletonList(DriveScopes.DRIVE_READONLY)
                ).setSelectedAccount(account.account)
                viewModel.initDriveService(account, credential)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(signedInAccount) {
        signedInAccount?.let { account ->
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singletonList(DriveScopes.DRIVE_READONLY)
            ).setSelectedAccount(account.account)
            viewModel.initDriveService(account, credential)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Meine Rezepte") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (signedInAccount == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        signInLauncher.launch(client.signInIntent)
                    }) {
                        Text("Mit Google anmelden")
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchRecipes(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nach Zutaten suchen...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                val groupedRecipes = recipes.groupBy { it.category ?: "Rezepte (Hauptordner)" }
                
                PullToRefreshBox(
                    isRefreshing = isSearching,
                    onRefresh = { viewModel.loadRecipes() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        groupedRecipes.toSortedMap().forEach { (category, categoryRecipes) ->
                            stickyHeader {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = category,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            items(categoryRecipes) { recipe ->
                                RecipeItem(recipe)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeItem(recipe: Recipe) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                recipe.driveUrl?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
            if (recipe.category != null) {
                Text(text = "Kategorie: ${recipe.category}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = "PDF Rezept", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
