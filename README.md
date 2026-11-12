# GladBooth

Photobooth til Android. Tryk på knappen, så tæller den 3, 2, 1, Smil!, blinker hvidt og viser billedet som et polaroid, der bliver fremkaldt.

## Installation

Hent `GladBooth.apk` fra den nyeste udgivelse og installer den på telefonen:
https://github.com/kaspersvangren/GladBooth/releases/latest/download/GladBooth.apk

Første gang spørger appen om lov til at bruge kameraet. Det er den eneste tilladelse.

## Billederne

Hvert gemt billede bliver til to filer i telefonens billedmappe:

- `Billeder/GladBooth`: det rene billede
- `Billeder/GladBooth Polaroid`: billedet med polaroid-ramme og tekst

Indstillinger åbnes ved at holde en finger i øverste venstre hjørne i et sekund.

## Opbygning

- `index.html` er hele photoboothen. Den kan også åbnes i en browser, hvor den kører i prøvetilstand uden at gemme filer.
- `app/` er Android-skallen, der viser siden i fuld skærm og skriver billederne til billedmappen.
- GitHub bygger APK'en, hver gang der lægges ændringer på `main`.

Signeringsnøglen `app/gladbooth.keystore` ligger åbent i repo'et, så nye udgaver kan installeres oven på de gamle. Den beskytter ikke noget hemmeligt. Installer kun APK'er fra dette repo.

Polaroid-lyden er `polaroid_600` af freesound_community.
