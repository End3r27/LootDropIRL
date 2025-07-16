package com.end3r.lootdropirl

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class LootDropApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable Firestore offline persistence
        val firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings

        // Sign in anonymously for basic functionality
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LootDropApp", "Anonymous sign-in successful")
                } else {
                    Log.e("LootDropApp", "Anonymous sign-in failed", task.exception)
                }
            }
    }
}