package com.example.controlinv.auth

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

const val SUPABASE_URL = "https://dsargvagjnoobyfkmait.supabase.co"
const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRzYXJndmFnam5vb2J5ZmttYWl0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQzMDMzMTMsImV4cCI6MjA3OTg3OTMxM30.WVPoQzzUL5VapBXNUUSMrqQNh_8caE1ci061qfIsCIA"

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Storage)
}
