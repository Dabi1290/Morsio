# Morsio

App personale per contare le calorie: fotografi il pasto, l'IA (Gemini, gratis) stima alimenti, grammi e macro, e tu puoi correggere tutto. Le foto vanno su un tuo server, dove un ospite le vede per 7 giorni tramite un link segreto.

| Cartella | Cosa contiene |
|---|---|
| `CalSnap/` | App iOS (SwiftUI) con widget, più il server delle foto in `server/` |
| `MorsioAndroid/` | App Android (Kotlin + Compose) con widget |
| `dieta_*.json` | Piani dieta da importare nella scheda Piano |
| `Diet1/` | La vecchia app della dieta: ha un suo repository e qui non va toccata |

Le due app non condividono il diario (ognuna salva i dati sul proprio telefono). Usano però lo stesso server delle foto e lo stesso link per l'ospite.

---

## 1. Server delle foto (LXC su Proxmox)

1. In Proxmox crea un container **Debian 12** (bastano 1 core, 256 MB di RAM e 2 GB di disco).
2. Copia i file nel container (sostituisci `IP-DEL-CT`):
   ```sh
   scp CalSnap/server/calsnap_server.py CalSnap/server/setup.sh root@IP-DEL-CT:/root/
   ```
   Se `root` non riesce a entrare via SSH, entra dalla console di Proxmox (`pct enter <id>`) e aggiungi la tua chiave a `/root/.ssh/authorized_keys`, oppure usa `ssh-copy-id`.
3. Nel container:
   ```sh
   cd /root && sh setup.sh
   ```
   Alla fine stampa due cose da conservare:
   - la **chiave upload**, da inserire nelle app;
   - il **link ospite** (`https://<tuo-dominio>/g/<token>/`), da dare a chi deve vedere i pasti.

   Le chiavi stanno in `/etc/calsnap.env`. Il server ascolta sulla porta **8080** e gira come servizio `calsnap`.
4. **HTTPS:** iOS accetta `http://` solo in rete locale, quindi da fuori casa serve un indirizzo `https://`. Il modo più semplice è un **Cloudflare Tunnel** (oppure un reverse proxy come Caddy o Nginx Proxy Manager) che punta a `http://IP-DEL-CT:8080`.

Per **aggiornare il server** dopo una modifica a `calsnap_server.py`:
```sh
scp CalSnap/server/calsnap_server.py root@IP-DEL-CT:/usr/local/bin/ && ssh root@IP-DEL-CT systemctl restart calsnap
```

Il server cancella da solo, ogni ora, le foto più vecchie di 7 giorni.

---

## 2. Chiave Gemini (gratuita)

1. Vai su **aistudio.google.com** e accedi con un account Google.
2. Apri **Get API key › Create API key** e copia la chiave.
3. Inseriscila nelle impostazioni dell'app (vedi il punto 5).

Il modello predefinito è `gemini-flash-latest`. Se è sovraccarico, l'app passa da sola a Flash-Lite.

---

## 3. App iOS

Serve un Mac con **Xcode 26** e **xcodegen** (`brew install xcodegen`).

```sh
cd CalSnap
xcodegen generate        # crea CalSnap.xcodeproj da project.yml
open CalSnap.xcodeproj
```

1. In Xcode seleziona il target **CalSnap**, poi **Signing & Capabilities**, e scegli il tuo Team. Fallo anche per il target **CalSnapWidget**. Se usi un account gratuito, potresti dover cambiare il bundle id e l'App Group (`group.com.dlab.calsnap`) in `project.yml`.
2. Collega l'iPhone, selezionalo come destinazione e premi **Run** (⌘R).
3. Sull'iPhone: **Impostazioni › Generali › VPN e gestione dispositivi**, poi autorizza il tuo profilo sviluppatore.

Con un account Apple gratuito l'app scade dopo 7 giorni: basta reinstallarla da Xcode, e i dati restano.

L'icona si rigenera con `swift make_icon.swift CalSnap/Assets.xcassets/AppIcon.appiconset ../MorsioAndroid/app/src/main/res/drawable-nodpi`.

---

## 4. App Android

**Installare l'APK già pronto**
1. Copia `MorsioAndroid/app/build/outputs/apk/release/app-release.apk` sul telefono (Drive, Telegram, email o cavo).
2. Aprilo, consenti **Installa app sconosciute** per quella sorgente e, se compare l'avviso di Play Protect, scegli **Installa comunque**.

In alternativa, con il cavo e il **Debug USB** attivo (da Opzioni sviluppatore, che si sbloccano toccando 7 volte "Numero build"):
```sh
~/Documents/SDK/android/platform-tools/adb install -r MorsioAndroid/app/build/outputs/apk/release/app-release.apk
```

**Ricompilare** (serve il JDK di Android Studio, perché Gradle non funziona con il JDK 25 di sistema):
```sh
cd MorsioAndroid
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease
```
In alternativa apri la cartella `MorsioAndroid` con Android Studio.

> ⚠️ L'APK è firmato con la chiave di debug di questo Mac (`~/.android/debug.keystore`). Gli aggiornamenti si installano sopra la versione precedente, senza perdere i dati, **solo se firmati con la stessa chiave**: fanne un backup.

---

## 5. Configurare l'app

Le impostazioni sono uguali su iOS e Android. Si aprono dall'icona ⚙️ in alto nel Diario.

| Voce | Cosa inserire |
|---|---|
| Obiettivo giornaliero | Le tue kcal al giorno |
| Promemoria | Accendi i pasti che vuoi ricordare e scegli l'orario. Se il pasto è già nel diario, quel giorno la notifica non arriva |
| Gemini › Chiave API | La chiave del punto 2 |
| Server foto › Indirizzo | `https://tuo-dominio` (in casa va bene anche `http://IP-DEL-CT:8080`) |
| Server foto › Chiave upload | La chiave stampata da `setup.sh` |

Se lasci vuoto l'indirizzo del server, le foto restano solo sul telefono.

**Piano dieta:** scarica sul telefono uno dei file `dieta_*.json` e importalo da **Piano › Importa piano**. Nel Diario compaiono i pasti previsti, già con calorie e macro: un tocco li aggiunge senza usare l'IA.

**Widget:** iOS: tieni premuto sulla Home › **+** › Morsio. Android: tieni premuto sulla Home › **Widget** › Morsio.

---

## 6. Uso quotidiano

- **Aggiungi pasto** con la foto, la galleria, il barcode o lo **Storico** (pasti e alimenti già inseriti, riusati senza IA), poi **Analizza con IA**. Il pasto si salva subito e l'analisi continua in background.
- Tocca un pasto per correggerlo: cambiando i grammi le kcal si ricalcolano, e toccando le kcal di un alimento puoi correggere i macro.
- **Barcode non trovato:** inserisci nome e valori per 100 g dall'etichetta. Dopo il salvataggio l'app riconosce quel codice.

---

## 7. Test

```sh
CalSnap/run_tests.sh          # server Python + test iOS sul simulatore (GEMINI_KEY=... per provare anche Gemini)
MorsioAndroid/run_tests.sh    # test JVM Android, compreso l'upload sul server locale
```
