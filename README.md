# Kravos Sports

Lična Android aplikacija za sportske rezultate (sideload), po uzoru na Apple Sports / 365Scores, sa „premium“ funkcijama, bez reklama.

## Šta ima

- **Rezultati**: 20 sportova (fudbal, košarka, tenis, rukomet, odbojka, vaterpolo, hokej, futsal, MMA, pikado, snuker…), traka datuma ±14 dana, režim **Uživo** sa brojem utakmica po sportu, osvežavanje na 15 s dok traju utakmice. Redosled liga: omiljene → Srbija → evropski kupovi → svetska takmičenja → velikih 5 → ostatak Evrope → ostalo.
- **Utakmica**: rezultat sa minutom uživo, strelci, rezultat po periodima/setovima, tok utakmice (golovi, kartoni, izmene, VAR), momentum napada, ključna i kompletna statistika po poluvremenima, **postave na terenu sa ocenama**, klupa i odsutni, **heatmapa tima i svakog igrača**, prosečne pozicije, **mapa šuteva sa xG/xGOT**, forma pred meč, tabela sa označenim timovima, međusobni dueli. Za košarku i ostale sportove: tabela statistike igrača (box score).
- **Lige**: tabela sa zonama (LŠ, LE, ispadanje…), rezultati i raspored po kolima, najbolji igrači (strelci, asistenti, ocene, xG…), izbor sezone.
- **Timovi i igrači**: naredne/poslednje utakmice, forma, tabele; profil igrača.
- **Omiljeno**: timovi i lige (zvezdica), njihove utakmice na vrhu rezultata.
- **Widget** sa utakmicama omiljenih timova (uživo / danas / sledeća).
- **Ažuriranje iz aplikacije**: proverava GitHub Releases ovog repoa i instalira novu verziju jednim dodirom.

## Izvor podataka

SofaScore (nezvanični API, samo za ličnu upotrebu). Ako direktni zahtevi budu blokirani, aplikacija automatski prelazi na skriveni WebView koji šalje zahteve sa sofascore.com (prolazi zaštitu jer je pravi Chromium).

## Build

GitHub Actions builduje potpisan APK na svaki push na `main` i kači ga na Releases; posle toga emulator prođe kroz sve ekrane (snimci ekrana i logovi idu na granu `ci-logs`).
Potpisivanje: `signing/release.jks.enc` (sopstveni ključ ove aplikacije), CI treba repo secret `KP_SIGNING_PASS`.
