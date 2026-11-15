# Home Manager

App Android per gestire gli oggetti (ad esempio il guardaroba) tenuti in più abitazioni.

## Funzioni

- Più residenze, con aggiunta, rinomina ed eliminazione.
- Capi con foto (galleria o fotocamera), descrizione e categoria.
- Trascinamento: tieni premuto un capo e trascinalo su una residenza per spostarlo.
- Filtro per categoria; dati salvati solo sul telefono.

## Scaricare l'APK

Vai su **Releases** e scarica `HomeManager.apk` dall'ultima build. Richiede Android 8.0 o superiore.
Ogni push su `main` ricompila l'app con GitHub Actions e pubblica una nuova release.

## Note sulla firma

Le build sono firmate con il keystore in `keystore/homemanager.jks` (password pubbliche, pensato per uso personale).
Cosi' ogni nuova versione si installa sopra la precedente senza perdere i dati.

## Compilare in locale

```
./gradlew assembleRelease
```

Il file si trova in `app/build/outputs/apk/release/`.
