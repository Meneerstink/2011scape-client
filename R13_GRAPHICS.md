# R13 — clientgraphics, 31 augustus 2026

Scope: uitsluitend de client. Home, game-server, cache, accounts en bestaande startbestanden zijn niet gewijzigd. De draaiende client/server zijn niet herstart.

## Gebruik

In de nieuwe client: **Graphics → Restore RSPS Performance v1 defaults**.
De knop herstelt alleen het grafische profiel. Geluid, venstermodus, cursorvoorkeur en de gekozen werkende renderer blijven behouden.
Wijzigingen worden op de game-thread verwerkt wanneer het login-, lobby- of gamescherm gereed is, niet rechtstreeks vanuit het venstermenu.

Bestaande geldige instellingen worden bij het laden behouden. Alleen ontbrekende/onleesbare/afgebroken instellingen krijgen het standaardprofiel. Instellingen zijn lokaal per clientinstallatie/gebruiker, niet een verplichte reset per spelaccount of login.

## Profiel v1

- Anti-aliasing, bloom, character/scenery shadows, extra lighting, advanced water, sky detail en fog uit.
- Daken altijd verborgen; brightness 3; standaard build area.
- Idle animations 1; particles 1 (0 als de bestaande geheugengrens dit vereist).
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

Nog NIET als bewezen beschouwen:
- Visuele bediening van de nieuwe menuknop in een ingelogde game.
- Werkelijke OpenGL/DirectX-drivers op de laptop, GPU-crashes buiten initialisatie.
- FPS voor/na, netwerkvertraging, alle combat-effecten.
- Fresh-client/saved-client/login/relogin met de echte spelclient.

Niet opnieuw de live client starten zolang Claude/owner die gebruikt. Bij het volgende geschikte testmoment:
1. Oude client afsluiten; nieuwe JAR starten met dezelfde bestaande verbindingsconfiguratie.
2. In home de herstelknop proberen; vast/resizable en eigen graphics/audio-keuzes controleren.
3. Afsluiten/starten en login/relogin: eigen keuzes moeten behouden blijven.
4. Met een apart tijdelijk voorkeurenbestand verse/corrupte instellingen controleren; bestaande gebruikersvoorkeuren niet verwijderen.
5. Dezelfde camerahoek/scène vergelijken voor FPS; effecten, bank en home-decor visueel controleren.
6. Native fallback testen met een afzonderlijke testinstallatie, niet door live native-bestanden te wissen.

R13 is technisch geïmplementeerd en automatisch getest; visuele/live-client-acceptatie blijft open.
