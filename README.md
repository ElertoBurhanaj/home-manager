# Home Inventory

App Android per catalogare gli oggetti di casa (e delle case al mare, in montagna, in affitto...) con foto, valore e garanzia.

## Funzioni

- **Più residenze**, ognuna con la sua icona; aggiunta, rinomina ed eliminazione.
- **Oggetti completi**: più foto (galleria o fotocamera, con scelta della copertina), categoria, stanza o posizione, quantità, valore, scadenza della garanzia, note.
- **Ricerca** in tutte le residenze (nome, note, categoria, stanza).
- **Ordinamento**: manuale, per nome, più recenti, valore più alto, preferiti prima. Filtri per categoria e preferiti.
- **Preferiti** con la stella direttamente sulla scheda.
- **Riepilogo** per residenza: oggetti, valore stimato, garanzie in scadenza; schermata con statistiche per residenza e categoria.
- **Trascinamento**: tieni premuto un oggetto e trascinalo su una residenza per spostarlo, o su un altro oggetto per riordinarlo.
- **Duplica** un oggetto con le sue foto.
- **Backup completo** in un file ZIP (dati e foto) e ripristino; **esportazione CSV** apribile con Excel.
- Tema chiaro e scuro. Dati salvati solo sul telefono.

## Aggiornamento da "Home Manager"

Il nome visibile è cambiato in Home Inventory, ma l'identificativo interno dell'app e la firma sono rimasti gli stessi:
installando il nuovo APK sopra la vecchia versione i dati esistenti (residenze, oggetti, foto) vengono mantenuti.

## Scaricare l'APK

Vai su **Releases** e scarica `HomeInventory.apk` dall'ultima build. Richiede Android 8.0 o superiore.
Ogni push su `main` ricompila l'app con GitHub Actions e pubblica una nuova release.

## Note sulla firma

Le build sono firmate con il keystore in `keystore/homemanager.jks` (password pubbliche, pensato per uso personale).
Così ogni nuova versione si installa sopra la precedente senza perdere i dati.

## Compilare in locale

```
./gradlew assembleRelease
```

Il file si trova in `app/build/outputs/apk/release/`.
