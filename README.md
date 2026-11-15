<div align="center">

# 🥗 Morsio

**Fotografi il piatto, Morsio conta le calorie.**

App per iPhone e Android: l'IA riconosce alimenti, grammi e macro da una foto,
tu correggi quello che serve, e un tuo server privato mostra i pasti a chi ti segue.

![iOS](https://img.shields.io/badge/iOS-SwiftUI-0A84FF?logo=apple&logoColor=white)
![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?logo=android&logoColor=white)
![Server](https://img.shields.io/badge/server-Python-3776AB?logo=python&logoColor=white)
![IA](https://img.shields.io/badge/IA-Gemini%20(gratis)-8E75B2?logo=googlegemini&logoColor=white)
![Licenza](https://img.shields.io/badge/licenza-PolyForm%20Noncommercial-orange)

</div>

---

## ✨ Cosa fa

- 📸 **Foto → calorie.** Scatta o scegli una foto: Gemini stima ogni alimento con grammi, kcal, proteine, carboidrati e grassi. L'analisi continua in background, il pasto è già salvato.
- ✏️ **Tutto correggibile.** Cambi i grammi e le kcal si ricalcolano; tocchi un alimento e ne correggi i macro.
- 🔎 **Barcode e storico.** Leggi il codice a barre di una confezione, oppure riusi pasti e alimenti già inseriti senza chiamare l'IA.
- 📋 **Piano dieta.** Importi un piano settimanale in JSON: nel diario compaiono i pasti previsti, già con calorie e macro, e un tocco li aggiunge.
- 📅 **Diario per giorno.** Anelli di avanzamento giorno per giorno, anche nei giorni passati e futuri, e quanto stai seguendo il piano.
- 🔔 **Promemoria intelligenti.** Ti ricorda i pasti agli orari che scegli, ma solo se non li hai già segnati.
- 🧩 **Widget** per iOS e Android con le calorie di oggi.
- 👀 **Link per l'ospite.** Le foto vanno sul tuo server; chi ha il link segreto (nutrizionista, coach, amico) vede i pasti degli ultimi 7 giorni.

Niente account, niente abbonamenti: il diario resta sul telefono e le API usate sono gratuite.

## 🗂️ Struttura

| Cartella | Cosa contiene |
|---|---|
| [`CalSnap/`](CalSnap) | App iOS (SwiftUI) con widget, e il server delle foto in [`server/`](CalSnap/server) |
| [`MorsioAndroid/`](MorsioAndroid) | App Android (Kotlin + Jetpack Compose) con widget |
| [`esempio_piano.json`](esempio_piano.json) | Un piano dieta d'esempio da importare |

Le due app non condividono il diario: ognuna salva i dati sul proprio telefono. Usano però lo stesso server delle foto e lo stesso link per l'ospite.

---

## 🚀 Installazione

### 1. Server delle foto

Gira in un container Linux qualsiasi (per esempio un LXC **Debian 12** su Proxmox: bastano 1 core, 256 MB di RAM e 2 GB di disco).

1. Copia i file nel container (sostituisci `IP-DEL-SERVER`):
   ```sh
   scp CalSnap/server/calsnap_server.py CalSnap/server/setup.sh root@IP-DEL-SERVER:/root/
   ```
2. Nel container:
   ```sh
   cd /root && sh setup.sh
   ```
   Alla fine stampa due cose da conservare:
   - la **chiave upload**, da inserire nelle app;
   - il **link ospite** (`https://<tuo-dominio>/g/<token>/`), da dare a chi deve vedere i pasti.

   Le chiavi stanno in `/etc/calsnap.env`. Il server ascolta sulla porta **8080** e gira come servizio `calsnap`.
3. **HTTPS:** iOS accetta `http://` solo in rete locale, quindi da fuori casa serve un indirizzo `https://`. Il modo più semplice è un **Cloudflare Tunnel**, oppure un reverse proxy come Caddy o Nginx Proxy Manager, che punta a `http://IP-DEL-SERVER:8080`.

Per aggiornarlo dopo una modifica:
```sh
scp CalSnap/server/calsnap_server.py root@IP-DEL-SERVER:/usr/local/bin/ && ssh root@IP-DEL-SERVER systemctl restart calsnap
```
Ogni ora il server cancella da solo le foto più vecchie di 7 giorni.

### 2. Chiave Gemini (gratuita)

1. Vai su [aistudio.google.com](https://aistudio.google.com) e accedi con un account Google.
2. Apri **Get API key › Create API key** e copia la chiave.
3. Incollala nelle impostazioni dell'app (punto 5).

Il modello predefinito è `gemini-flash-latest`; se è sovraccarico, l'app passa da sola a Flash-Lite.

### 3. App iOS

Serve un Mac con **Xcode 26** e **xcodegen** (`brew install xcodegen`).

```sh
cd CalSnap
xcodegen generate        # crea CalSnap.xcodeproj da project.yml
open CalSnap.xcodeproj
```

1. Per i target **CalSnap** e **CalSnapWidget** apri **Signing & Capabilities** e scegli il tuo Team. Con un account gratuito potresti dover cambiare il bundle id e l'App Group (`group.com.dlab.calsnap`) in `project.yml`.
2. Collega l'iPhone, selezionalo come destinazione e premi **Run** (⌘R).
3. Sull'iPhone: **Impostazioni › Generali › VPN e gestione dispositivi**, poi autorizza il profilo sviluppatore.

Con un account Apple gratuito l'app scade dopo 7 giorni: basta reinstallarla da Xcode, e i dati restano.

### 4. App Android

Serve il JDK di Android Studio (Gradle non funziona con JDK 25 o più recenti):

```sh
cd MorsioAndroid
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

In alternativa apri la cartella `MorsioAndroid` con Android Studio. Per installare l'APK senza cavo, copialo sul telefono e consenti **Installa app sconosciute**.

> ⚠️ Gli aggiornamenti si installano sopra la versione precedente, senza perdere i dati, solo se l'APK è firmato con la stessa chiave: conserva il keystore.

### 5. Configurazione

Le impostazioni sono uguali su iOS e Android e si aprono dall'icona ⚙️ in alto nel Diario.

| Voce | Cosa inserire |
|---|---|
| Obiettivo giornaliero | Le tue kcal al giorno |
| Promemoria | I pasti da ricordare e l'orario. Se il pasto è già nel diario, quel giorno la notifica non arriva |
| Gemini › Chiave API | La chiave del punto 2 |
| Server foto › Indirizzo | `https://tuo-dominio` (in casa va bene anche `http://IP-DEL-SERVER:8080`) |
| Server foto › Chiave upload | La chiave stampata da `setup.sh` |

Se lasci vuoto l'indirizzo del server, le foto restano solo sul telefono.

**Widget:** su iOS tieni premuto sulla Home › **+** › Morsio; su Android tieni premuto sulla Home › **Widget** › Morsio.

---

## 📋 Piano dieta

Un piano è un file JSON con 7 giorni (`Lun` … `Dom`). Ogni giorno ha i pasti `Colazione`, `Spuntini`, `Pranzo` e `Cena`, e ogni pasto ha i suoi alimenti con i valori nutrizionali. Le alternative si scrivono nel nome, separate da "oppure":

```json
{
  "name": "Riso 80 g oppure pasta 80 g oppure farro 80 g",
  "kcal": 285, "protein": 6, "carbs": 62, "fat": 0.8
}
```

Parti da [`esempio_piano.json`](esempio_piano.json), copialo sul telefono e importalo da **Piano › Importa piano**.

## 🧪 Test

```sh
CalSnap/run_tests.sh          # server Python + test iOS sul simulatore (GEMINI_KEY=... per provare anche Gemini)
MorsioAndroid/run_tests.sh    # test JVM Android, compreso l'upload sul server locale
```

Con più simulatori dallo stesso nome, scegline uno per id: `SIM=id=<UDID> CalSnap/run_tests.sh`.

## 📄 Licenza

Morsio è distribuito con la [PolyForm Noncommercial License 1.0.0](LICENSE): puoi usarlo, studiarlo, modificarlo e condividerlo per qualsiasi scopo **non commerciale**. Per un uso commerciale serve un accordo a parte con l'autore.
