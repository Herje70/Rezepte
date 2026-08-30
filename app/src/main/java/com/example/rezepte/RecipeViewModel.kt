package com.example.rezepte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Collections

class RecipeViewModel : ViewModel() {

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var driveServiceHelper: DriveServiceHelper? = null

    fun initDriveService(account: GoogleSignInAccount, credential: GoogleAccountCredential) {
        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Rezepte")
            .build()
        
        driveServiceHelper = DriveServiceHelper(driveService)
        loadRecipes()
    }

    fun loadRecipes() {
        viewModelScope.launch {
            _isSearching.value = true
            val list = driveServiceHelper?.listRecipes() ?: emptyList()
            _recipes.value = list
            _isSearching.value = false
        }
    }

    fun searchRecipes(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            if (query.isBlank()) {
                loadRecipes()
            } else {
                val list = driveServiceHelper?.searchRecipes(query) ?: emptyList()
                _recipes.value = list
            }
            _isSearching.value = false
        }
    }
}
