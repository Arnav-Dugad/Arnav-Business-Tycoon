# R8 rules for the app. kotlinx.serialization keeps its own rules via the library.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Credential Manager loads its Play services provider by reflection (androidx.credentials docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
