# ScrollMate

> Mehr Kontrolle über deine Aufmerksamkeit – auf deine Art.

ScrollMate ist ein Hackathon-MVP für Android: ein freundlicher Coach, der – nach ausdrücklicher Zustimmung – echte Nutzungszeiten zeigt, nach selbst gesetzten Regeln erinnert und einen ehrlichen **Fokus-Modus** anbietet. Gesperrt wird nur, wenn du den **Abendmodus** einschaltest – dann deckt ScrollMate abends ausgewählte Apps mit einem ruhigen Vollbild ab. Es gibt keine `INTERNET`-Permission, kein Konto, und alle Daten bleiben auf dem Gerät.

---

## Build-Status

Das Projekt wird bei jedem Push auf GitHub Actions gebaut (`.github/workflows/build.yml`, Ubuntu 24.04 mit vorinstalliertem Android SDK): `:core`-Tests, der DataStore-Neustart-Test in `:app` und `assembleDebug`. Die fertige APK liegt im jeweiligen Lauf unter **Artifacts → scrollmate-debug-apk**. Schlägt ein Build fehl, erscheinen die Compile-Fehler als Hinweis („Build errors“) in der Lauf-Übersicht.

Ohne Netz kann `:core` auch mit `tools/verify-core-offline.sh` geprüft werden.

---|---|
| `:core` kompiliert und getestet | **Ja** – 67 JUnit-Tests grün (siehe unten, wie) |
| `./gradlew :core:test` über Gradle | **Nicht ausgeführt** – in der Build-Umgebung waren Maven Central, Google Maven und `services.gradle.org` gesperrt, Gradle konnte das Kotlin-Plugin nicht laden |
| `:app` gebaut (`assembleDebug`) | **Nein** – kein Android SDK verfügbar und nicht installierbar (dl.google.com gesperrt) |
| `:app` Quellcode | Vollständig vorhanden; Kotlin-Parser ohne Syntaxfehler, alle `R.string`-Referenzen und Format-Argumente per Skript geprüft. **Typprüfung gegen das Android SDK steht aus.** |

Die `:core`-Tests wurden mit dem Kotlin-Compiler 2.0.21 und JUnit 4.13.2 aus der lokalen Gradle-Distribution ausgeführt: `tools/verify-core-offline.sh`. Lokal mit Internetzugang ist der normale Weg `./gradlew :core:test`.

---

## Voraussetzungen und Befehle

- **JDK 17 oder neuer** (AGP 8.13 benötigt mindestens JDK 17)
- **Android SDK** mit Platform **API 36** (compileSdk/targetSdk 36) – am einfachsten über Android Studio
- Gerät oder Emulator mit **Android 8.0 (API 26)** oder neuer
- Internetzugang beim ersten Build (Gradle lädt Wrapper 8.14.3 und Abhängigkeiten)

```bash
# 1. SDK-Pfad setzen (oder Projekt in Android Studio öffnen, das legt local.properties an)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# 2. Reine Logik-Tests (laufen ohne SDK und ohne Emulator)
./gradlew :core:test

# 3. JVM-Test: Einstellungen überstehen einen Neustart (DataStore, kein Emulator nötig)
./gradlew :app:testDebugUnitTest

# 4. App bauen und installieren
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ohne Internet, aber mit lokaler Gradle-Installation: `tools/verify-core-offline.sh [GRADLE_HOME]` kompiliert und testet `:core` direkt.

### Gewählte Versionen

AGP 8.13.0 · Gradle 8.14.3 · Kotlin 2.2.21 · KSP 2.2.21-2.0.5 · Compose BOM 2025.09.00 · Room 2.7.2 · DataStore 1.1.7 · WorkManager 2.10.1 · compileSdk/targetSdk 36 · minSdk 26.
Google Play verlangt seit dem 31.08.2026 für neue Apps und Updates **targetSdk 36** (Android 16). AGP 9.x ist inzwischen stabil; siehe „Annahmen“, warum hier noch 8.13 verwendet wird.

---

## Projektstruktur

```
core/   reines Kotlin/JVM, keine Android-Abhängigkeit
        model/      UserPreferences, DailyGoal, AppUsageEntry, CoachingStyle, RuleId, TimeWindow,
                    ReminderSettings, NotificationRecord, FocusSession, RestrictionStatus, DataSourceState
        usage/      UsageAggregator (Events → Minuten je lokalem Tag, Mitternacht + Sommerzeit korrekt), DailyUsage
        rules/      RuleEngine, CoachingRule, NotificationKeys (Idempotenz)
        coach/      Coach-Interface + RuleBasedCoach (austauschbar, z. B. später KI)
        focus/      FocusSessions (Start, vorzeitiges Ende, regulärer Ablauf, Dauer)
        history/    7-Tage-Verlauf, Durchschnitt erst ab 5 Live-Tagen
        progress/   Fortschritt, Restzeit, Überschreitung
        datasource/ Live / Berechtigung fehlt / Demo
        demo/       deterministischer Demo-Datensatz (7 Tage, 5 Apps)
        restriction/AppRestrictionService + NoOpRestrictionService (meldet UNAVAILABLE)
        prefs/      Codec für DataStore-Strings
app/    Android: Compose-UI (5 Tabs), ViewModels, DataStore, Room, UsageStatsManager,
        Benachrichtigungen, WorkManager. Manuelle Konstruktor-Injektion in AppContainer.
tools/  verify-core-offline.sh
```

Alle Zeitfunktionen in `:core` bekommen `Clock`/`ZonedDateTime` bzw. `ZoneId` übergeben und sind damit deterministisch testbar.

---

## Neu in diesem Update

**Tonalität des Coaches (5 Stimmen, mit Avatar).** Zusätzlich zum Coaching-Stil (wann erinnert wird) wählst du in den Einstellungen, *wie* der Coach klingt. Alle Erinnerungen (70 %, 100 %, Wiederholung, App-Limit, Check-in, Fokus-Ende, Schlafenszeit) passen sich an. Der Avatar erscheint in der Titelleiste, im Coach-Tab und in den Benachrichtigungen.

| Stimme | Avatar | Beispiel bei 70 % |
|---|---|---|
| Mausig (Standard) | 🐭 | „Psst … 70 % geschafft, Mäuschen! 🐭 Zeit für ’ne kleine Pause?“ |
| Harsch | 🔥 | „70 % weg. Null Selbstbeherrschung. Weg das Teil!“ |
| Lässig | 🕶️ | „70 % erreicht! Zeit für ’nen Shake-out, Bro/Sis 🤙“ |
| Neutral | 🤖 | „70 % des Tageslimits erreicht.“ |
| Aggressiv | 💥 | „SCHON 70 %! LEG DAS TEIL WEG!“ |

Harsch und Aggressiv sind bewusst konfrontativ und nur auf ausdrücklichen Wunsch aktiv.

**Gesundheitscoach (Coach-Tab).**
- *Statt zu scrollen:* 4-7-8-Atmung (mit animiertem Atemkreis), 10 Kniebeugen/Liegestütze, ein Glas Wasser, 2 Minuten Dehnen, Blick aus dem Fenster. Ein Vorschlag wechselt stündlich; abends werden nur ruhige Alternativen vorgeschlagen.
- *Schlaf-Coach:* Fragen antippen („Warum schlafe ich nach dem Handy schlechter?“, „Was hilft beim Einschlafen?“, „Meine Gedanken kreisen“, „Ich wache nachts auf …“) und kurze, praktische Antworten erhalten (Blaulicht/Melatonin, Reizüberflutung, kühles Zimmer 16–18 °C, Brain-Dump, Digital Detox). Mit Hinweis, dass es keine medizinische Beratung ist.
- *Konstruktive Rückfragen:* „Fühlst du dich nach dem Scrollen heute besser oder müder?“ und „Was hast du gerade gesucht?“ – mit passender, freiwilliger Antwort.

**Abendmodus (Schlafenszeit-Sperre).** Du stellst Schlafenszeit (Standard 22:30), Aufstehzeit (06:30) und den Vorlauf (Standard 30 Min.) ein und wählst die Apps. Beim Einschalten werden installierte Feed-Apps (Instagram, TikTok, YouTube, …) vorgeschlagen. Ab Vorlauf bis zur Aufstehzeit legt ScrollMate über diese Apps ein ruhiges Vollbild mit geführter 4-7-8-Atmung und dem Hinweis auf die Schlafenszeit. „Zum Startbildschirm“ und die Zurück-Taste verlassen die App; „Heute Abend aussetzen“ pausiert bis zum nächsten Abend (damit niemand ausgesperrt wird). Zu Beginn des Vorlaufs kommt zusätzlich eine Erinnerung in der gewählten Tonalität.
*Technik:* Vordergrunddienst (Typ `specialUse`) prüft während des Zeitfensters etwa jede Sekunde per `UsageStatsManager.queryEvents`, welche App vorne ist, und zeigt das Overlay über `TYPE_APPLICATION_OVERLAY`. Außerhalb des Fensters wacht er nur etwa einmal pro Minute auf. Keine Bedienungshilfe (Accessibility) nötig.

**Erscheinungsbild.** Umschalter Hell / Dunkel / Wie im System. Hell: Creme- und Soft-Grau-Flächen mit dunkler Schrift. Dunkel: tiefes Slate (`#0f172a`) mit dunklen Karten. Statusleisten-Symbole folgen der App-Einstellung.

**Status-Glow.** Ring und Rahmen der Heute-Karte leuchten je nach Verbrauch: unter 70 % grün (`#22c55e`), 70–99 % orange (`#f59e0b`), ab 100 % rot (`#ef4444`). Zusätzlich steht der Status als Text daneben („Sicherer Bereich“, „Warnstufe“, „Limit überschritten“), damit er nicht nur an der Farbe hängt.

**Kategorien-Analyse (Verlauf → Kategorien).** Nutzung nach Soziales & Social Media, Unterhaltung, Arbeit & Lernen, Informationsmedien & News und Sonstiges – für heute oder 7 Tage, mit Prozent-Balken, absoluter Zeit und den größten Apps je Kategorie. Zuordnung: Liste bekannter Apps, sonst die Kategorie, die eine App selbst angibt (`ApplicationInfo.category`).

---

## Berechtigungen

| Berechtigung | Wofür | Erteilen | Widerrufen |
|---|---|---|---|
| `PACKAGE_USAGE_STATS` (Nutzungsdatenzugriff) | Vordergrundzeit pro App lesen | In der App „Zugriff auf Nutzungsdaten erteilen“ → Systemeinstellungen → ScrollMate → erlauben. Per adb: `adb shell appops set com.scrollmate.app GET_USAGE_STATS allow` | Systemeinstellungen → Apps → Spezieller App-Zugriff → Nutzungsdatenzugriff. Per adb: `adb shell appops set com.scrollmate.app GET_USAGE_STATS ignore` |
| `POST_NOTIFICATIONS` (ab Android 13) | Erinnerungen als Benachrichtigung | Laufzeitdialog im Onboarding oder in den Einstellungen. Per adb: `adb shell pm grant com.scrollmate.app android.permission.POST_NOTIFICATIONS` | Systemeinstellungen der App. Per adb: `adb shell pm revoke com.scrollmate.app android.permission.POST_NOTIFICATIONS` |

| `SYSTEM_ALERT_WINDOW` („Über anderen Apps einblenden“) | Nur für den Abendmodus: Overlay über gesperrten Apps | Einstellungen → Abendmodus → „Einblenden über anderen Apps erlauben“. Per adb: `adb shell appops set com.scrollmate.app SYSTEM_ALERT_WINDOW allow` | Systemeinstellungen → Apps → Spezieller App-Zugriff → Über anderen Apps einblenden |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Abendmodus-Dienst (dauerhafte, leise Benachrichtigung „Abendmodus ist bereit“) | automatisch | Abendmodus ausschalten |
| `RECEIVE_BOOT_COMPLETED` | Abendmodus nach Neustart wieder bereitstellen | automatisch | – |

- Der Status des Nutzungszugriffs wird bei **jedem Resume** neu über `AppOpsManager` geprüft (und einmal pro Minute, solange die App sichtbar ist). Ein Widerruf wird sofort als **„Berechtigung fehlt“** angezeigt.
- Ohne Benachrichtigungs-Erlaubnis erscheinen alle Coach-Nachrichten **nur in der App** (Coach-Tab, „Erinnerungen heute“, markiert mit „nur in der App“).
- Package Visibility: `<queries>` für Launcher- und Home-Intents, **kein** `QUERY_ALL_PACKAGES`.
- `INTERNET` wird per `tools:node="remove"` aktiv aus dem gemergten Manifest entfernt. Backup und Gerätetransfer sind ausgeschlossen.

---

## Live- vs. Demo-Daten

Der Status-Chip oben rechts zeigt immer genau einen Zustand:

| Chip | Bedeutung |
|---|---|
| **Live-Daten** | Nutzungszugriff erteilt, Demo-Modus aus. Zahlen stammen aus `UsageStatsManager.queryEvents`. |
| **Berechtigung fehlt** | Kein Zugriff, Demo-Modus aus. Heute- und Verlauf-Tab zeigen eine Karte mit „Zugriff erteilen“ oder „Demo-Daten“. Check-in, Coach und Fokus funktionieren trotzdem. |
| **Demo-Daten** | Demo-Modus an (hat immer Vorrang). Jeder Screen mit Zahlen zeigt zusätzlich den Hinweis „Demo-Daten: … nicht deine echte Nutzung“; Benachrichtigungen tragen „(Demo)“ im Titel. |

**Live-Berechnung:** Vordergrundzeit wird aus Resume/Pause-Ereignissen pro **lokalem Kalendertag** berechnet, nicht aus den Buckets von `queryUsageStats`. Sessions über Mitternacht werden aufgeteilt; Sommerzeit (23-/25-Stunden-Tage) wird korrekt behandelt. Bildschirm aus, Sperrbildschirm und Herunterfahren schließen offene Sessions. Weil Android Rohereignisse nur begrenzt vorhält, speichert ScrollMate eine Tageszusammenfassung pro App in Room (60 Tage). Für abgeschlossene Tage wird nie ein kleinerer Wert übernommen.

**Demo-Datensatz:** 7 Tage × 5 fiktive Apps („Kurzvideos“, „Social Feed“, „Foto-Feed“, „Nachrichten“, „Messenger“, Paketnamen mit Präfix `demo.`). Heute: 87 Min., also 72 % des Standardlimits von 2 Stunden. Live- und Demo-Daten werden nie vermischt.

---

## Feature-Status

„Geprüft“ sagt, wie weit die Funktion in dieser Umgebung überprüft werden konnte: **Unit-Test** = Logik in `:core` per JUnit grün; **CI-Build** = kompiliert auf GitHub Actions, aber nicht automatisiert auf einem Gerät getestet.

| Funktion | Status | Geprüft | Anmerkung |
|---|---|---|---|
| Onboarding (5 Screens, überspringbar) | funktioniert | CI-Build | Nutzen → Ziel/Limit → Stil → Datenerklärung → Berechtigung **oder** Demo |
| Dashboard „Heute“ mit Ring, Restzeit, Top-Apps, Nächstes | funktioniert | CI-Build + Unit-Test (Fortschritt) | Status-Glow grün/orange/rot mit Textlabel |
| Status-Chip Live / Berechtigung fehlt / Demo | funktioniert | CI-Build + Unit-Test (Übergänge) | Prüfung bei jedem Resume |
| Ziele und Regeln in den Einstellungen | funktioniert | CI-Build + JVM-Test geschrieben | Tageslimit, App-Limits, Schwellen, Zeitfenster, Stil, Fokus-Dauer, Check-in-Zeit, max. Erinnerungen, Regeln einzeln abschaltbar |
| Persistenz über Neustart (DataStore) | funktioniert | JVM-Test geschrieben, **nicht ausgeführt** | `SettingsPersistenceTest`, läuft mit `:app:testDebugUnitTest` |
| Coaching-Stile Sanft / Aktiv / Konsequent / Fokuszeiten | funktioniert | Unit-Test | Konsequent: Fokus-Vorschlag bei 100 % + Wiederholung nach Intervall, ohne Sperre |
| Regelbasierter Coach | funktioniert | Unit-Test | Deterministisch, genau eine optionale Handlung, Texte in `strings.xml` |
| Check-in „Scrollst du gerade bewusst?“ | funktioniert | CI-Build + Unit-Test | Im Coach-Tab jederzeit, täglich per Hintergrundprüfung |
| Fokus-Session (Warnung → Check-in → Timer → Reflexion) | funktioniert | CI-Build + Unit-Test | Übersteht Prozessende (Room); Hinweis „Andere Apps werden nicht gesperrt.“ immer sichtbar |
| Demo-Modus | funktioniert | Unit-Test (Datensatz) + Code | Schalter in den Einstellungen und im Onboarding |
| Echte Nutzungsdaten (`queryEvents`) | funktioniert | Unit-Test (Aggregation) + Code | Mitternacht, Sommerzeit, Bildschirm aus |
| Benachrichtigungen + Fallback in-app | funktioniert | CI-Build | `POST_NOTIFICATIONS` zur Laufzeit; ohne Erlaubnis nur in-app |
| Schwellen-Checks im Hintergrund | funktioniert | CI-Build + Unit-Test (Regeln) | WorkManager alle 15 Min. → Verzögerung bis ca. 15 Min. oder mehr |
| Idempotenz der Erinnerungen | funktioniert | Unit-Test + Code | Schlüssel `Datum|Regel|Schwelle[|Zusatz]`, Room-Insert mit IGNORE |
| Verlauf: 7-Tage-Diagramm + Zielerreichung | funktioniert | Unit-Test + Code | Liste mit Text und Symbol, nicht nur Farbe |
| Vergleich mit persönlichem Durchschnitt | funktioniert | Unit-Test | Erst ab 5 abgeschlossenen Live-Tagen, vorher erklärt |
| App-Auswahl (Package Visibility) | funktioniert | CI-Build | Leere Auswahl = alle Apps außer ScrollMate und Launcher |
| Export (CSV) und Löschen aller Daten (P2) | funktioniert | CI-Build | Löschen setzt auch die Einstellungen zurück → Onboarding |
| Notizen nach Coach-Fragen | funktioniert | CI-Build | Nur lokal, im Export enthalten |
| Meilensteine / wachsende Pflanze (P2) | nicht umgesetzt | – | Bewusst weggelassen, bis P0/P1 auf Gerät verifiziert sind |
| 5 Tonalitäten mit Avatar | funktioniert | CI-Build + Unit-Test | Gilt für alle Erinnerungen und Benachrichtigungen |
| Gesundheitscoach (Alternativen, Atemübung, Schlaf-Coach, Rückfragen) | funktioniert | CI-Build + Unit-Test | Nur allgemeine Tipps, keine Diagnosen |
| Abendmodus mit Overlay-Sperre | funktioniert | CI-Build + Unit-Test (Zeitlogik) | Braucht Overlay-Erlaubnis und Nutzungszugriff; nicht auf Gerät verifiziert |
| Theme-Umschalter Hell/Dunkel/System | funktioniert | CI-Build | |
| Kategorien-Analyse | funktioniert | CI-Build + Unit-Test | Heute oder 7 Tage |
| Sperre tagsüber / bei Limit | nicht umgesetzt | – | Gesperrt wird nur im Abendmodus |
| KI-Coach | nicht umgesetzt | – | Interface `Coach` ist vorbereitet |
| iOS | nicht umgesetzt | – | Plattformcode hinter Interfaces |
| Dark Mode, große Schrift, `contentDescription` | funktioniert | CI-Build | Alle Screens scrollbar; Touch-Ziele ≥ 48 dp; nicht mit TalkBack getestet |

**„Simuliert“** betrifft nur Demo-Daten: Im Demo-Modus sind alle Zahlen simuliert, die Logik dahinter ist dieselbe wie bei Live-Daten.

---

## Wie die Erinnerungen funktionieren

1. `CoachCheckWorker` (WorkManager, periodisch alle 15 Minuten) lädt die Nutzung, führt die `RuleEngine` aus und sendet **höchstens eine** Erinnerung pro Lauf.
2. Wurden seit dem letzten Lauf mehrere Schwellen überschritten (z. B. 70 % und 90 %), wird nur die höchste gezeigt; die niedrigeren werden still als erledigt gespeichert, damit sie nicht verspätet nachkommen.
3. Priorität: Wiederholung (Konsequent) → Gesamtschwelle → App-Limit → Check-in.
4. Die maximale Anzahl pro Tag gilt für Schwellen- und Wiederholungs-Erinnerungen; der selbst gewählte Check-in zählt nicht mit.
5. Während einer laufenden Fokus-Session gibt es keine Schwellen-Erinnerungen.
6. **Check-in und Fokus-Ende ohne Exact-Alarm-Permission:** Der Check-in kommt mit der nächsten periodischen Prüfung nach der eingestellten Uhrzeit (Toleranz bis ca. 15 Minuten, bei Doze/Energiesparen auch mehr). Das Fokus-Ende wird mit einem einmaligen WorkManager-Job zur geplanten Zeit gemeldet (inexakt); in der App läuft der Timer sekundengenau.
7. Für die Vorführung gibt es im Coach-Tab **„Regeln jetzt prüfen“**.

---

## Bekannte Grenzen

- **Verzögerung:** Schwellen werden frühestens nach bis zu ~15 Minuten erkannt (WorkManager-Minimum), unter Doze oder im Energiesparmodus später. Das steht auch im Einstellungs-Screen.
- **Sperre nur abends.** Tagsüber und bei Überschreitung des Limits blockiert ScrollMate nichts; nur der Abendmodus deckt gewählte Apps ab.
- **Abendmodus-Grenzen:** Erkennung per Abfrage etwa jede Sekunde, also mit kurzer Verzögerung; kostet abends etwas Akku. Wird ScrollMate vom System beendet oder darf der Dienst nach einem Neustart nicht starten, greift die Sperre erst wieder nach dem nächsten Öffnen von ScrollMate. Split-Screen und Bild-in-Bild werden nicht gesondert behandelt. Manche Hersteller und Emulatoren (z. B. BlueStacks) schränken Overlays oder Hintergrunddienste ein.
- **Google Play:** Ein `specialUse`-Vordergrunddienst und Overlays über anderen Apps müssen bei einer Veröffentlichung begründet werden; für den Hackathon-APK spielt das keine Rolle.
- **Split-Screen / Bild-in-Bild:** Es wird immer nur eine App als im Vordergrund gezählt.
- **Vorhaltezeit:** Android hält Rohereignisse nur einige Tage vor. Tage vor der Installation oder vor der Berechtigung können fehlen.
- **App-Limits** erinnern nur bei 100 %, nicht an Zwischenschwellen (bewusst ruhig gehalten).
- **Demo und Live am selben Tag:** Erinnerungen, die im Demo-Modus ausgelöst wurden, zählen für die Idempotenz mit. Wer am selben Tag von Demo auf Live wechselt, bekommt eine bereits gesendete Schwelle nicht noch einmal.
- **Zeitzonenwechsel:** Tage werden in der aktuellen Gerätezone berechnet; bereits gespeicherte Tageszusammenfassungen werden nicht rückwirkend umgerechnet.
- **Nicht verifiziert:** `:app` wurde nicht kompiliert und nicht auf einem Gerät getestet (siehe „Build-Status“). Barrierefreiheit wurde nicht mit TalkBack geprüft.

---

## Annahmen

- **AGP 8.13 statt 9.x:** AGP 9 ändert die Kotlin-Integration grundlegend. Da in dieser Umgebung nicht gebaut werden konnte, wurde die bewährte 8.13-Kombination gewählt; sie unterstützt API 36 und wird von aktuellen Android-Studio-Versionen unterstützt. Ein Upgrade mit dem AGP Upgrade Assistant ist der nächste Schritt.
- **Bibliotheksversionen** sind bewusst ein konsistenter Stand aus 2025, der zu AGP 8.13 passt, nicht die allerneuesten.
- **„Alle Apps“:** Ohne App-Auswahl zählt die gesamte Vordergrundzeit außer ScrollMate und dem Startbildschirm.
- **Check-in** gilt in allen Coaching-Stilen, auch bei „Fokuszeiten“ außerhalb der Zeitfenster; er ist eine selbst gewählte, tägliche Verabredung.
- **Max. Erinnerungen pro Tag** zählen nur Schwellen- und Wiederholungs-Erinnerungen, nicht den Check-in.
- **Zeitfenster** mit gleicher Start- und Endzeit gelten als ganzer Tag; Endzeit ist exklusiv.
- **Prozent** werden abgerundet: 69,9 % lösen die 70-%-Schwelle nicht aus.
- **„Okay, kurze Pause“** ist eine reine Bestätigung; es wird nichts geplant oder gesperrt.
- **Reflexionsnotizen** werden lokal gespeichert, nicht ausgewertet und sind im Export enthalten.
- **App-Sprache** ist ausschließlich Deutsch.

---

## Ausblick

**Echte Sperre (Android).** Mögliche Wege und was jeweils zu prüfen wäre:
- *AccessibilityService*, der beim Öffnen einer App einen eigenen Screen zeigt: technisch verbreitet, aber Google Play schränkt die Accessibility-API auf bestimmte Zwecke ein und verlangt Deklaration und deutliche Offenlegung; Ablehnungsrisiko prüfen.
- *Overlay* (`SYSTEM_ALERT_WINDOW`): leicht zu umgehen, zusätzliche Sonderberechtigung, UX- und Policy-Fragen.
- *Device Owner / Profile Owner* (z. B. App-Pausieren, Lock-Task): nur für verwaltete Geräte realistisch, nicht für normale Verbraucher-Installationen.
- *VpnService*: sperrt nur Netzwerkverkehr, nicht die App; Play erlaubt VPN nur für VPN-Kernfunktionen.
- Systemfunktionen wie die App-Timer von Digital Wellbeing sind für Drittanbieter nicht direkt nutzbar.

**iOS.** Mit `FamilyControls` (Autorisierung, auch für Einzelpersonen), `DeviceActivity` (Zeitpläne und Schwellen per Extension) und `ManagedSettings` (Shields für Apps) ist eine echte Sperre möglich. Dafür braucht es das Family-Controls-Entitlement von Apple. Die Logik aus `:core` lässt sich nach Swift portieren oder mit Kotlin Multiplatform teilen; `AppRestrictionService` ist die vorbereitete Schnittstelle.

**KI-Coach.** Eine zweite Implementierung von `Coach`, idealerweise auf dem Gerät, damit die Datenschutzzusage „alles bleibt auf dem Gerät“ hält. Leitplanken wie heute: keine Diagnosen, keine Vermutungen über Stimmung oder Gründe, genau eine freiwillige nächste Handlung.

---

## 3-Minuten-Demo-Skript

**Vorbereitung:** App frisch installiert. Benachrichtigungen erlaubt. Optional: Nutzungszugriff bereits erteilt, um am Ende kurz Live-Daten zu zeigen.

**0:00 – Problem (20 s)**
„Wir alle kennen es: kurz aufs Handy, und eine halbe Stunde ist weg. Die meisten Apps dagegen arbeiten mit Schuld, Streaks oder harten Sperren. ScrollMate ist ein freundlicher Coach – und ehrlich darüber, was er kann.“

**0:20 – Onboarding (40 s)**
- Screen 1: Leitsatz zeigen. „Gesperrt wird nur abends, wenn du es willst – das sagen wir sofort.“
- Screen 2: Ziel „Abends weniger scrollen“, Limit auf 2 Std. lassen.
- Screen 3: Stil **Konsequent** wählen, den Satz „Andere Apps werden dabei nicht gesperrt“ zeigen.
- Screen 4: Datenschutz: nur Vordergrundzeiten, nichts verlässt das Gerät, kein Internet.
- Screen 5: **„Mit Demo-Daten fortfahren“**.

**1:00 – Heute (30 s)**
Status-Chip **Demo-Daten** und Demo-Hinweis zeigen. Ring und Rahmen leuchten orange (Warnstufe, 72 %), „Noch 33 Min. bis zu deinem Tagesziel“, Top-Apps.

**1:30 – Coach und Check-in (40 s)**
- Coach-Tab: Tageseinschätzung.
- „Regeln jetzt prüfen“ → Benachrichtigung „(Demo) Kurze Zwischenbilanz: Du hast 70 % deines Tagesziels erreicht …“. Nochmal tippen → „Gerade ist keine Erinnerung fällig“: „Keine doppelten Erinnerungen, auch nicht nach Neustart.“ (Gilt vor der Check-in-Zeit, Standard 20:00 – danach kommt beim zweiten Tippen der Check-in.)
- Check-in „Scrollst du gerade bewusst?“ → **„Eher nicht“** → Vorschlag einer Fokus-Session → Button.

**2:10 – Fokus-Session (30 s)**
Hinweis „Andere Apps werden nicht gesperrt.“ zeigen. Auf 5 Min. stellen, starten, Timer läuft, nach ein paar Sekunden **„Vorzeitig beenden“** → „Jede Minute zählt“ → kurze Reflexion eintippen, speichern.

*Optional, wenn Zeit ist:* In den Einstellungen die Tonalität auf **Harsch 🔥** stellen – das Beispiel ändert sich sofort, der Avatar oben auch. Im Coach-Tab „Statt zu scrollen“ → 4-7-8-Atmung starten.

**2:40 – Verlauf und Abschluss (20 s)**
7-Tage-Diagramm mit Limitlinie; oben auf **Kategorien** umschalten: Soziales, Unterhaltung, News in Prozent. „Den Vergleich mit dem Durchschnitt zeigen wir erst ab fünf echten Tagen – vorher wäre er Rauschen.“ Optional: in den Einstellungen Demo-Modus aus → Chip wechselt auf **Live-Daten** oder **Berechtigung fehlt**.
Schlusssatz: „Mehr Kontrolle über deine Aufmerksamkeit – auf deine Art.“
