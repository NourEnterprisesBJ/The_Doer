# Règles ProGuard/R8 pour The Doer.
# Vide pour l'instant (Sprint 1) — à enrichir quand on passera
# en build release, notamment pour les libs natives (Vosk/JNA,
# ONNX Runtime) qui nécessitent souvent des règles -keep
# spécifiques pour ne pas casser leurs appels réflexifs/JNI.

# Exemple de ce qu'on ajoutera plus tard :
# -keep class org.vosk.** { *; }
# -keep class com.sun.jna.** { *; }
