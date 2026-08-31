# R13 — clientgraphics, 31 augustus 2026

Scope: uitsluitend de client. Home, game-server, cache, accounts en bestaande startbestanden zijn niet gewijzigd. Voor de aansluitende visuele controle is alleen de client herstart; Claude en de servers zijn niet door Codex herstart.

## Gebruik

In de nieuwe client: **Graphics → Restore RSPS Performance v2 defaults**.
De knop herstelt alleen het grafische profiel. Geluid, venstermodus, cursorvoorkeur en de gekozen werkende renderer blijven behouden.
Wijzigingen worden op de game-thread verwerkt wanneer het login-, lobby- of gamescherm gereed is, niet rechtstreeks vanuit het venstermenu.

Bestaande geldige instellingen worden bij het laden behouden. Alleen ontbrekende/onleesbare/afgebroken instellingen krijgen het standaardprofiel. Instellingen zijn lokaal per clientinstallatie/gebruiker, niet een verplichte reset per spelaccount of login.

## Profiel v2

- Anti-aliasing, bloom, character/scenery shadows, extra lighting, advanced water, sky detail en fog uit.
- Daken altijd verborgen; brightness 3; standaard build area.
- Idle animations 0/Few; particles 1/Medium (0 als de bestaande geheugengrens dit vereist).
- Visuele controle wees uit dat de v1-waarde 1 bij idle animations Many betekent. V2 corrigeert dit naar de daadwerkelijk gecontroleerde Few-waarde 0; bestaande persoonlijke keuzes worden niet bij login overschreven.
- Textures, ground blending, ground decor en flickering effects blijven aan. Ground decor is bewust behouden om home-aankleding niet weg te filteren.
- Bestaande CPU-default: 4 op meerdere cores, anders 2.
- De ingebouwde interface toont CUSTOM, omdat dit niet exact het oude LOW/MIN-profiel is.
- Combatanimaties/projectielen worden niet uitgeschakeld; daadwerkelijke zichtbaarheid van alle specials moet nog visueel worden gecontroleerd.

## Renderer en opslag

Nieuwe instellingen kiezen OpenGL als de native beschikbaar is; anders DirectX op ondersteund Windows met beschikbare native, anders Java software. Bestaande native- en driverinitialisatie blijft de echte ondersteuning controleren. Dit is een compatibiliteitsvoorkeur, geen claim dat OpenGL altijd sneller is.

Een mislukte native/driver/heap-initialisatie probeert eenmaal Java software. De mislukte renderer wordt die sessie niet opnieuw geprobeerd, ook niet wanneer de interface alsnog de mislukte keuze probeert op te slaan. De bijbehorende opgeslagen voorkeur wordt software; overige keuzes blijven behouden voor zover de renderer ze ondersteunt. Softwarefalen en ThreadDeath worden niet eindeloos onderschept.

Voorkeuren worden volledig naar een tijdelijk bestand geschreven, gesynchroniseerd en vervolgens vervangen (atomisch waar het bestandssysteem dit ondersteunt). Een schrijffout geeft een begrensde waarschuwing. Er zijn geen nieuwe netwerkvelden: het bestaande voorkeurenpakket blijft versie 25 met 35 gebruikte bytes.

Aanvullende gevonden fouten:
- CustomCursors negeerde de opgeslagen waarde in zijn constructor.
- CPU usage, water detail en volume gebruikten een onmogelijke bereikcontrole met AND.
- Renderer-ID 4 werd toegelaten hoewel er geen renderer 4 bestaat.
- Een afgebroken modern voorkeurenpakket kon tijdens decode een uitzondering en gedeeltelijke toestand veroorzaken. De oorspronkelijke buitenste bestandsloader ving veel van deze fouten al op; dit is geen claim dat elke corrupte voorkeur de oude client liet crashen.
- De oude rendererfallback kon dezelfde mislukte renderer opnieuw aanroepen.

## Bewijs en grenzen

Uitgevoerd in een aparte kopie, JDK 11, Gradle offline, één worker:
- :runescape:check (inclusief r13Regression)
- :client:jar
- Losse JAR --help zonder netwerk/login.

De regressies testen verse instellingen, alle afbreekpunten van het v25-pakket, onbekende/afgebroken oudere formaten, behoud van v23/v24-keuzes, exacte v25-roundtrip, herstel met behoud van overige instellingen, ongeldige waarden, lokale bestandsschrijf-/herlaadtests, mislukt schrijven en native-/driver-/heapfallback zonder herhaling.

## Aanvullende visuele controle

V2: 120 automatische assertions geslaagd. Gebruiker logde zelf in. Login, home-weergave, instellingenvensters en fixed/resizable zijn visueel gecontroleerd. OpenGL werkte op deze laptop. De v2-herstelknop is ingelogd gebruikt: verbinding bleef intact en de scene bleef renderen; profiel op schijf gecontroleerd, inclusief idle 0/Few en behouden renderer/venstermodus.

Bij v1 bleven geldige voorkeuren behouden en was het opgeslagen profiel na clientherstart byte voor byte gelijk. V2 startte met de eerder gekozen Few-instelling. Na de laatste v2-menuactie geen extra herstart uitgevoerd.

FPS in home bij losse opnamen meestal 45–50; resizable ook 43 en eenmaal 34. Tijdens een achtergrondbuild 17. Laatste twee opnamen na v2-herstel: 50. Geen gecontroleerde benchmark of bewezen FPS-winst.

Een testuitvoeringsfout (opnieuw bouwen van een JAR die nog draaide) gaf classloadingfouten. De eigen testclient is vervangen door een vaste afzonderlijke client-v2.jar; fout tijdens vervolg niet opnieuw gezien. Die snapshot blijft ongewijzigd. Normale launcher is niet aangepast.

Open: niet-fatale JPEG-startupwaarschuwing, visuele DirectX/native-failure-tests, alle combat-effecten en gecontroleerde prestatietest. Verse/corrupte voorkeuren zijn alleen automatisch getest. Deze controle verklaart home-ontwerp en serverinteracties niet voltooid.

Volledig lokaal bewijs: C:\Users\Pa\.codex\.chatgpt-projects\g-p-6a92eefdff4081918c46ce4bfebed767\r13-live-check\RESULTAAT.md.
