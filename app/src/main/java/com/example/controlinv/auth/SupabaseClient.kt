package com.example.controlinv.auth

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest



val supabase = createSupabaseClient(
    supabaseUrl = "https://dsargvagjnoobyfkmait.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRzYXJndmFnam5vb2J5ZmttYWl0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQzMDMzMTMsImV4cCI6MjA3OTg3OTMxM30.WVPoQzzUL5VapBXNUUSMrqQNh_8caE1ci061qfIsCIA"
) {
    install(Auth)
    install(Postgrest)

    install(Auth)
    install(Postgrest)
}