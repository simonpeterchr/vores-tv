# Vores TV – web

Vores TV i en browser: Søg, Hjem, Live-TV, Sport, Film og Serier – samme funktioner og design som tv-appen, på computer, telefon og iPad.

En lille server (ren Node.js, ingen pakker) kører på en maskine på hjemmenettet, fx den pc der i forvejen kører Plex. Den taler med IPTV-udbyderen og videresender streams, så **login og udbyderens adresser aldrig når browseren**.

## Kom i gang

Kræver [Node.js](https://nodejs.org) 20 eller nyere.

```
node server.js
```

Åbn `http://<maskinens-ip>:8766` i en browser på hjemmenettet. Første gang:

- **Nemmest:** åbn telefon-siden på et tv med Vores TV, tryk **Del login** og vælg serveren på listen.
- **Eller** skriv login (server, brugernavn, kode eller M3U-link) direkte på siden.

Data (login, indstillinger, cache) gemmes i `data/` ved siden af `server.js`. Mappen må aldrig deles.

## Start automatisk på Windows

Læg filerne i `C:\VoresTV`, og kør i PowerShell som administrator:

```powershell
$a = New-ScheduledTaskAction -Execute "C:\Program Files\nodejs\node.exe" -Argument "server.js" -WorkingDirectory "C:\VoresTV"
$t = New-ScheduledTaskTrigger -AtStartup
$s = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit 0 -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
$p = New-ScheduledTaskPrincipal -UserId "S-1-5-18" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName "VoresTV" -Action $a -Trigger $t -Settings $s -Principal $p -Force
New-NetFirewallRule -DisplayName "Vores TV web" -Direction Inbound -Protocol TCP -LocalPort 8766 -Action Allow -RemoteAddress LocalSubnet -Profile Any
Start-ScheduledTask VoresTV
```

Opdatering fra en Mac/Linux med SSH-adgang: `./deploy-windows.sh bruger@ip`.

## Brugere og adgang udefra

- **Hjemme på nettet** kræves ingen kode – man er husstandens bruger (`hjem`).
- **Udefra** (fx via en [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) til `http://localhost:8766`) kræver alt login: sider, data og streams. Forkerte koder spærres efter 8 forsøg på et kvarter; koder gemmes som scrypt-hash.
- Under **Indstillinger → Brugere** kan husstanden oprette egne brugere med eget IPTV-login, egne favoritter og eget "fortsæt".

## Tv'erne i huset

Tv-appen (fra version 0.43) finder selv serveren på hjemmenettet og deler husstandens indstillinger med den: favoritter, viste grupper, senest sete, film/serie-grupper, sport-favoritter og "fortsæt". Felt for felt vinder den nyeste ændring.

## Chromecast og AirPlay

- **Chromecast:** i Chrome/Edge, når siden er åbnet over **https** (fx via tunnelen). Tv'et henter selv streamen via et signeret link, der gælder i 12 timer.
- **AirPlay:** i Safari, til Apple TV og AirPlay-tv'er.

## Afspilning

Live-tv afspilles som HLS (hls.js) med MPEG-TS som reserve (mpegts.js). Film og serier i MP4 virker overalt; MKV kun i Chrome og Edge.

Kanallogoer hentes fra [tv-logo/tv-logos](https://github.com/tv-logo/tv-logos).
