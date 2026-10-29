# Vores TV

En mørk, rolig IPTV-app til Android TV (NVIDIA Shield, Chromecast med Google TV m.fl.) i Apple TV-stil.
Den samler live-TV, film og serier fra din egen Xtream Codes-udbyder ét sted.

> Vores TV indeholder **intet indhold** og ingen kanaler. Du skal selv have et abonnement hos en udbyder,
> og du logger ind med dine egne oplysninger.

## Det kan den

- **Hjem** – ny film som forside, *Fortsæt* hvor du slap, *Live nu* fra dine favoritkanaler, nyt i film og serier.
- **Live-TV** – lille live-forhåndsvisning, programinfo, tidslinje-guide med nu-linje og dine egne grupper.
- **Afspiller** – ▴▾ skifter kanal med det samme, OK åbner kanalpanelet, du kan taste kanalnummer.
- **Film og serier** – plakat-rækker, detaljeside, sæsoner og afsnit, fortsæt hvor du slap, næste afsnit af sig selv.
- **Lyd og undertekster** – vælg spor under afspilning (▴).
- **Søg på tværs** – kanaler, programmer, film og serier, eller kun én medietype.
  Sportsord virker: *fodbold*, *dart*, *håndbold*, *tennis* … finder også kampe på PPV-kanaler.
- **Færre kanaler** – kun de grupper og favoritter, du vælger. Rodede navne som `DK| DENMARK ᴴᴰ` bliver til `Denmark HD`.

## Installér på en Shield

1. Installér **Downloader** (af AFTVnews) fra Play Butik på Shield.
2. Åbn Downloader, og skriv adressen:

   ```
   https://github.com/simonpeterchr/vores-tv/releases/latest/download/VoresTV.apk
   ```

3. Tillad Downloader at installere apps, når Shield spørger, og tryk **Installér**.
4. Åbn **Vores TV**. Scan QR-koden med telefonen, og log ind med din udbyders server, brugernavn og kode
   – eller indsæt bare dit M3U-link. Login gemmes kun på tv'et.
5. Vælg dine kanaler: hold OK nede på en kanal for at gøre den til favorit, eller brug telefon-siden.

## Fjernbetjening

| Hvor | Tast | Gør |
|---|---|---|
| Live-guide | OK · hold OK | Se kanal · favorit |
| Live-afspiller | ▴▾ · OK · ◂▸ | Skift kanal · kanalpanel · skift gruppe i panelet |
| Film/serier | OK · ◂▸ · ▴ · ▾ | Pause · spol (hold = hurtigere) · lyd og tekster · næste afsnit |

## Byg selv

Kræver Android SDK (build-tools 34, platform 34) og JDK 17 – ingen Gradle.

```bash
./fetch-libs.sh   # henter Media3, Guava og zxing (med checksum)
./build.sh        # giver VoresTV.apk
```

Uden `signing.local` signeres med en lokal debug-nøgle.

## Licens

MIT – se [LICENSE](LICENSE). Tredjepartsbiblioteker: se [THIRD_PARTY.md](THIRD_PARTY.md).

## Vores TV i browseren

Mappen [`web/`](web/) indeholder en browser-udgave med de samme funktioner, der kører på en pc på hjemmenettet. Se [web/README.md](web/README.md).
